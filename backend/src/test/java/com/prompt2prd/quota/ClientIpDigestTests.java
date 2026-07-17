package com.prompt2prd.quota;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpDigestTests {

    @Test
    void createsAStableSaltedDigestWithoutRetainingTheRawAddress() {
        ClientIpDigest digest = new ClientIpDigest(
                new QuotaProperties(3, 1, 100, 30, 100, "salt-one"));

        String first = digest.digestAddress("203.0.113.42");
        String second = digest.digestAddress("203.0.113.42");
        String otherSalt = new ClientIpDigest(
                new QuotaProperties(3, 1, 100, 30, 100, "salt-two"))
                .digestAddress("203.0.113.42");

        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first).isEqualTo(second).isNotEqualTo(otherSalt);
        assertThat(first).doesNotContain("203.0.113.42");
    }

    @Test
    void usesTheActualRemoteAddressAndIgnoresForwardingHeaders() {
        ClientIpDigest digest = new ClientIpDigest(
                new QuotaProperties(3, 1, 100, 30, 100, "test-salt"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/quota")
                .remoteAddress(new InetSocketAddress("198.51.100.7", 443))
                .header("X-Forwarded-For", "203.0.113.99")
                .build();

        String actual = digest.from(MockServerWebExchange.from(request));

        assertThat(actual).isEqualTo(digest.digestAddress("198.51.100.7"));
        assertThat(actual).isNotEqualTo(digest.digestAddress("203.0.113.99"));
    }
}
