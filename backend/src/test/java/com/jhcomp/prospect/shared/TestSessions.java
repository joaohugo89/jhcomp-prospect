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
 * Logging in for integration tests.
 *
 * <p>Each login comes from a different origin address. The login limiter is a
 * bean of the Spring context, which is reused across test classes; with every
 * login coming from 127.0.0.1, the whole suite would share one per-network
 * quota and would start failing with 429 as it grew, in unpredictable
 * order.
 */
public final class TestSessions {

    /** Addresses in 198.18.0.0/15, a range reserved for testing (RFC 2544). */
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
