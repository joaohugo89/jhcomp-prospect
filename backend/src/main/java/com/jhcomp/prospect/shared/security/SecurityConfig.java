package com.jhcomp.prospect.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.jhcomp.prospect.shared.web.ApiErrorResponse;
import com.jhcomp.prospect.shared.web.ApiErrorType;

import tools.jackson.databind.ObjectMapper;

/**
 * Configuracao de seguranca (decisao D1, doc 07 §4 e §5).
 *
 * <p>Autenticacao por sessao com cookie {@code HttpOnly}, sem JWT e sem
 * refresh token. O cookie e configurado em {@code application.yml}
 * ({@code server.servlet.session.cookie}).
 *
 * <p>CSRF fica habilitado. Com autenticacao por cookie, desabilitar CSRF
 * abriria a aplicacao a requisicoes forjadas a partir de outro site, que e
 * exatamente o risco que o token previne. O token vai em um cookie legivel por
 * JavaScript, para que o frontend o reenvie no cabecalho — essa leitura e
 * intencional e nao reduz a protecao, que depende de o atacante nao conseguir
 * ler cookies de outra origem.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Codificador delegante: grava com BCrypt prefixando {@code {bcrypt}} e
     * consegue verificar hashes de outros algoritmos.
     *
     * <p>O prefixo e o que permitira migrar para Argon2id depois sem invalidar
     * as senhas existentes (doc 07 §4).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Repositorio do token CSRF.
     *
     * <p>O cookie precisa ser legivel por JavaScript, ao contrario do de
     * sessao: o frontend le o valor e o reenvia no cabecalho
     * {@code X-XSRF-TOKEN}. A protecao continua valendo porque um site
     * atacante nao consegue ler cookies de outra origem.
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        // Sem path "/", o cookie herda o context-path (/api/v1) e o JavaScript
        // da pagina, servida em /, nao consegue le-lo: o POST sairia sem o
        // cabecalho e seria recusado com 403 no navegador, ainda que os testes
        // de API passassem.
        repository.setCookiePath("/");

        return repository;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Mantem a comparacao de hash mesmo quando o usuario nao existe, para
        // que o tempo de resposta nao revele quais e-mails estao cadastrados.
        provider.setHideUserNotFoundExceptions(true);

        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            ObjectMapper objectMapper) throws Exception {

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                // Materializa o cookie do token: o Spring Security adia a
                // geracao do token, de modo que sem este filtro o cookie
                // XSRF-TOKEN so apareceria depois de alguem le-lo, e o
                // frontend nao teria o que enviar no primeiro POST.
                .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // A protecao contra fixacao de sessao e feita no proprio
                // endpoint de login, que troca o id da sessao apos autenticar.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(requests -> requests
                        // Rotas publicas: apenas o que precisa existir antes do login.
                        .requestMatchers("/health", "/auth/login", "/openapi/**", "/docs/**", "/swagger-ui/**")
                        .permitAll()
                        // Todo o resto exige autenticacao (doc 05 §12).
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(
                                unauthorizedEntryPoint(objectMapper))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(
                                        objectMapper,
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        ApiErrorType.FORBIDDEN,
                                        "You do not have permission to perform this operation",
                                        request.getRequestURI())))
                // Sem formLogin nem httpBasic: o login e o endpoint JSON
                // /auth/login, e uma pagina de login HTML nao faz sentido numa
                // API consumida por SPA.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Responde 401 no formato da API quando falta autenticacao.
     *
     * <p>Sem isso o Spring Security responderia com corpo proprio, e o
     * frontend veria dois formatos de erro diferentes.
     */
    private static org.springframework.security.web.AuthenticationEntryPoint unauthorizedEntryPoint(
            ObjectMapper objectMapper) {

        HttpStatusEntryPoint fallback = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);

        return (request, response, exception) -> {
            if (response.isCommitted()) {
                fallback.commence(request, response, exception);
                return;
            }
            writeError(
                    objectMapper,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    ApiErrorType.AUTHENTICATION_REQUIRED,
                    "Authentication is required for this operation",
                    request.getRequestURI());
        };
    }

    private static void writeError(
            ObjectMapper objectMapper,
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            ApiErrorType type,
            String detail,
            String instance) throws java.io.IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiErrorResponse body = ApiErrorResponse.of(type, status.value(), detail, instance);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
