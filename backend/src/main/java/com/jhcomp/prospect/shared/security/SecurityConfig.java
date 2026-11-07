package com.jhcomp.prospect.shared.security;

import java.io.IOException;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;
import com.jhcomp.prospect.shared.web.ApiErrorResponse;
import com.jhcomp.prospect.shared.web.ApiErrorType;

import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * Security configuration (decision D1, doc 07 §4 and §5).
 *
 * <p>Session authentication with an {@code HttpOnly} cookie, no JWT and no
 * refresh token. The cookie itself is configured in {@code application.yml}
 * ({@code server.servlet.session.cookie}).
 *
 * <p>CSRF stays enabled. With cookie authentication, disabling CSRF would open
 * the application to requests forged from another site, which is exactly the
 * risk the token prevents. The token travels in a cookie readable by
 * JavaScript so the frontend can echo it in a header — that readability is
 * intentional and does not weaken the protection, which rests on an attacker
 * being unable to read cookies from another origin.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * A delegating encoder: it writes BCrypt prefixed with {@code {bcrypt}}
     * and can verify hashes from other algorithms.
     *
     * <p>That prefix is what will allow a later migration to Argon2id without
     * invalidating the existing passwords (doc 07 §4).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** The CSRF token's cookie and header, under project-specific names (D22). */
    public static final String CSRF_COOKIE_NAME = "JH_XSRF";
    public static final String CSRF_HEADER_NAME = "X-JH-XSRF";

    /**
     * The CSRF token repository.
     *
     * <p>This cookie has to be readable by JavaScript, unlike the session one:
     * the frontend reads the value and echoes it in the
     * {@value #CSRF_HEADER_NAME} header. The protection still holds because an
     * attacking site cannot read cookies from another origin.
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        // Without path "/", the cookie inherits the context path (/api/v1)
        // and the JavaScript on the page served at / cannot read it: the POST
        // would go out without the header and be refused with 403 in the
        // browser, even though every API test passed.
        repository.setCookiePath("/");

        // A project-specific name rather than Spring's default, for two
        // concrete reasons:
        //
        // 1. Cookies are not isolated by port. Every service on localhost
        //    shares one cookie jar, so a generic name collides with any other
        //    project running on the same machine, and the browser starts
        //    sending two cookies with the same name.
        // 2. The server reads only the first cookie with that name. With a
        //    duplicate — from a collision between projects, or a leftover from
        //    an earlier path — it compares the header against the wrong cookie
        //    and refuses the request with 403 and no useful message.
        repository.setCookieName(CSRF_COOKIE_NAME);
        repository.setHeaderName(CSRF_HEADER_NAME);

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
        // Keeps the hash comparison even when the user does not exist, so the
        // response time does not reveal which e-mails are registered.
        provider.setHideUserNotFoundExceptions(true);

        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            OrganizationMemberRepository memberRepository,
            ObjectMapper objectMapper) throws Exception {

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                // Materialises the token cookie: Spring Security defers token
                // generation, so without this filter the cookie would only
                // appear after something read it, and the frontend would have
                // nothing to send on the first POST.
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // Before authorisation: the role and the organisation have to
                // be current when the access rules are evaluated, otherwise
                // revoking a permission would have no effect for as long as the
                // session lasts.
                .addFilterBefore(
                        new MembershipRevalidationFilter(memberRepository, securityContextRepository),
                        AuthorizationFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // Session fixation protection is handled by the login
                // endpoint itself, which rotates the session id after
                // authenticating.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(requests -> requests
                        // Public routes: only what has to exist before login.
                        .requestMatchers("/health", "/auth/login", "/openapi/**", "/docs/**", "/swagger-ui/**")
                        .permitAll()
                        // Open to whoever still has to change a temporary password (D28).
                        .requestMatchers("/auth/me", "/auth/logout", "/auth/password").authenticated()
                        // Everything else requires authentication (doc 05 §12) and a permanent password.
                        .anyRequest().access(SecurityConfig::authenticatedWithFinalPassword))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedEntryPoint(objectMapper))
                        .accessDeniedHandler((request, response, exception) -> {
                            if (requiresPasswordChange(SecurityContextHolder.getContext().getAuthentication())) {
                                writeError(objectMapper, response, HttpStatus.FORBIDDEN,
                                        ApiErrorType.PASSWORD_CHANGE_REQUIRED,
                                        "Change the temporary password before continuing", request.getRequestURI());
                                return;
                            }
                            writeError(
                                    objectMapper,
                                    response,
                                    HttpStatus.FORBIDDEN,
                                    ApiErrorType.FORBIDDEN,
                                    "You do not have permission to perform this operation",
                                    request.getRequestURI());
                        }))
                // No formLogin and no httpBasic: login is the JSON endpoint
                // /auth/login, and an HTML login page makes no sense in an API
                // consumed by a single-page application.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Authenticated, with no temporary password pending. An anonymous caller
     * still gets 401 from the entry point rather than 403: this rule only
     * denies someone who is already signed in.
     */
    private static AuthorizationDecision authenticatedWithFinalPassword(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        boolean authenticated = current != null && current.isAuthenticated()
                && current.getPrincipal() instanceof AuthenticatedUser;
        return new AuthorizationDecision(authenticated && !requiresPasswordChange(current));
    }

    private static boolean requiresPasswordChange(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.passwordChangeRequired();
    }

    /**
     * Answers 401 in the API's error format when authentication is missing.
     *
     * <p>Without this, Spring Security would answer with a body of its own and
     * the frontend would see two different error shapes.
     */
    private static AuthenticationEntryPoint unauthorizedEntryPoint(
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
            HttpServletResponse response,
            HttpStatus status,
            ApiErrorType type,
            String detail,
            String instance) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiErrorResponse body = ApiErrorResponse.of(type, status.value(), detail, instance);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
