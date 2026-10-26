package com.jhcomp.prospect.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClientNetwork")
class ClientNetworkTest {

    @Test
    @DisplayName("IPv4 e exato nos dois niveis")
    void ipv4IsExact() {
        assertThat(ClientNetwork.of("203.0.113.10"))
                .isEqualTo(new ClientNetwork("203.0.113.10", "203.0.113.10"));
    }

    @Test
    @DisplayName("IPv4 mapeado em IPv6 vira o IPv4")
    void mappedIpv4() {
        assertThat(ClientNetwork.of("::FFFF:203.0.113.10").subscriber()).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("IPv6 agrupa assinante em /64 e site em /48")
    void ipv6Prefixes() {
        var network = ClientNetwork.of("2001:db8:abcd:12:aaaa:bbbb:cccc:dddd");

        assertThat(network.subscriber()).isEqualTo("20010db8abcd0012::/64");
        assertThat(network.site()).isEqualTo("20010db8abcd::/48");
    }

    @Test
    @DisplayName("formas abreviada e completa do mesmo IPv6 coincidem")
    void ipv6CanonicalForm() {
        assertThat(ClientNetwork.of("2001:db8::1"))
                .isEqualTo(ClientNetwork.of("2001:0db8:0000:0000:0000:0000:0000:0002"));
    }

    @Test
    @DisplayName("valor nao literal e vazio nao escapam do limite")
    void nonLiteralIsKeptAsIs() {
        assertThat(ClientNetwork.of("nao-e-ip").site()).isEqualTo("nao-e-ip");
        assertThat(ClientNetwork.of(" ").site()).isEqualTo("unknown");
        assertThat(ClientNetwork.of(null).site()).isEqualTo("unknown");
    }
}
