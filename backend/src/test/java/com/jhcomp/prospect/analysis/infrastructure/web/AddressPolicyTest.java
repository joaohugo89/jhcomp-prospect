package com.jhcomp.prospect.analysis.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Protecao contra SSRF (regra 8 do produto, docs/07 §6). Unitario, sem rede. */
@DisplayName("AddressPolicy")
class AddressPolicyTest {

    private static boolean blocked(String literal) throws Exception {
        // getByName com literal de IP nao consulta DNS.
        return AddressPolicy.PUBLIC_ONLY.isBlocked(InetAddress.getByName(literal));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "127.0.0.1", "127.8.9.10", "0.0.0.0", "10.0.0.5", "172.16.0.1", "172.31.255.255", "192.168.1.1",
        "169.254.169.254", "100.64.0.1", "100.127.255.254", "192.0.0.8", "198.18.0.1", "224.0.0.1",
        "240.0.0.1", "255.255.255.255",
        "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1", "2001:db8::1",
        "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254", "64:ff9b::a00:1",
        // Formas que carregam IPv4 dentro do IPv6: recusadas por inteiro.
        "2002:7f00:1::1", "2002:a9fe:a9fe::1", "2001:0:4136:e378:8000:63bf:3fff:fdd2", "::7f00:1",
        "64:ff9b:1::a00:1", "2001:10::1", "100::1"
    })
    @DisplayName("bloqueia rede interna, metadata de nuvem, faixas reservadas e IPv4 embutido em IPv6")
    void blocksInternal(String address) throws Exception {
        assertThat(blocked(address)).describedAs(address).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "200.160.2.3", "100.128.0.1", "172.32.0.1", "2800:3f0:4001:80a::200e"})
    @DisplayName("permite internet publica")
    void allowsPublic(String address) throws Exception {
        assertThat(blocked(address)).describedAs(address).isFalse();
    }

    @Test
    @DisplayName("politica de teste libera so loopback, nada mais da rede interna")
    void testPolicyOnlyAllowsLoopback() throws Exception {
        AddressPolicy policy = AddressPolicy.allowingLoopbackForTests();

        assertThat(policy.isBlocked(InetAddress.getByName("127.0.0.1"))).isFalse();
        assertThat(policy.isBlocked(InetAddress.getByName("10.0.0.1"))).isTrue();
        assertThat(policy.isBlocked(InetAddress.getByName("169.254.169.254"))).isTrue();
    }
}
