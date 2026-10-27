package com.jhcomp.prospect.shared;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.jhcomp.prospect.identity.IdentityTestData;

/**
 * Login para testes de integracao.
 *
 * <p>Cada login sai de um endereco de origem diferente. O limitador de login e
 * um bean do contexto Spring, que e reaproveitado entre classes de teste; com
 * todos os logins vindos de 127.0.0.1, a suite inteira dividiria a mesma cota
 * por rede e passaria a falhar com 429 conforme crescesse, em ordem
 * imprevisivel.
 */
public final class TestSessions {

    /** Enderecos em 198.18.0.0/15, faixa reservada para testes (RFC 2544). */
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

    private TestSessions() {
    }

    public static MockHttpSession login(MockMvc mockMvc, IdentityTestData.Member member) throws Exception {
        int index = NEXT_ADDRESS.incrementAndGet();
        String address = "198.18." + ((index / 250) % 250) + "." + (index % 250 + 1);

        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(member.email(), member.password());

        return (MockHttpSession) mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(address);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession();
    }
}
