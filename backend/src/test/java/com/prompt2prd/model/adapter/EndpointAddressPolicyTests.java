package com.prompt2prd.model.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.prompt2prd.model.application.ModelGatewayException;
import org.junit.jupiter.api.Test;

class EndpointAddressPolicyTests {

    @Test
    void rejectsUnsupportedSchemesAndEmbeddedCredentials() {
        EndpointAddressPolicy policy = policyReturning("8.8.8.8", Set.of());

        assertRejected(policy, "ftp://models.example/v1");
        assertRejected(policy, "https://user:secret@models.example/v1");
    }

    @Test
    void publicHostsRequireHttps() {
        EndpointAddressPolicy policy = policyReturning("8.8.8.8", Set.of());

        assertRejected(policy, "http://models.example/v1");
        assertThat(policy.validate(URI.create("https://models.example/v1")).addresses())
                .extracting(InetAddress::getHostAddress)
                .containsExactly("8.8.8.8");
    }

    @Test
    void loopbackMayUseHttpButMetadataIsAlwaysRestrictedWithoutAllowlist() {
        EndpointAddressPolicy loopback = policyReturning("127.0.0.1", Set.of());
        EndpointAddressPolicy metadata = policyReturning("169.254.169.254", Set.of());

        assertThat(loopback.validate(URI.create("http://localhost:8080/v1")).addresses()).hasSize(1);
        assertRejected(loopback, "http://attacker-controlled.example/v1");
        assertRejected(metadata, "https://metadata.example/v1");
    }

    @Test
    void privateHttpsHostRequiresAndHonorsExplicitAllowlist() {
        EndpointAddressPolicy denied = policyReturning("10.1.2.3", Set.of());
        EndpointAddressPolicy allowedByHost = policyReturning("10.1.2.3", Set.of("models.internal"));
        EndpointAddressPolicy allowedByIp = policyReturning("10.1.2.3", Set.of("10.1.2.3"));

        assertRejected(denied, "https://models.internal/v1");
        assertThat(allowedByHost.validate(URI.create("https://models.internal/v1")).addresses()).hasSize(1);
        assertThat(allowedByIp.validate(URI.create("https://models.internal/v1")).addresses()).hasSize(1);
    }

    @Test
    void resolvesOnlyOnceAndReturnsPinnedAddressesToPreventDnsRebinding() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        EndpointAddressPolicy policy = new EndpointAddressPolicy(Set.of(), host -> {
            int attempt = resolutions.incrementAndGet();
            return List.of(InetAddress.getByName(attempt == 1 ? "8.8.8.8" : "127.0.0.1"));
        });

        EndpointAddressPolicy.ValidatedEndpoint endpoint = policy.validate(URI.create("https://models.example/v1"));

        assertThat(resolutions).hasValue(1);
        assertThat(endpoint.addresses()).extracting(InetAddress::getHostAddress).containsExactly("8.8.8.8");
    }

    @Test
    void exposesStableProviderPresetAddresses() {
        assertThat(ModelProviderPreset.OPENAI.baseUrl()).hasToString("https://api.openai.com/v1");
        assertThat(ModelProviderPreset.DEEPSEEK.baseUrl()).hasToString("https://api.deepseek.com/v1");
        assertThat(ModelProviderPreset.QWEN.baseUrl())
                .hasToString("https://dashscope.aliyuncs.com/compatible-mode/v1");
    }

    private static EndpointAddressPolicy policyReturning(String address, Set<String> allowlist) {
        return new EndpointAddressPolicy(allowlist,
                host -> List.of(InetAddress.getByName(address)));
    }

    private static void assertRejected(EndpointAddressPolicy policy, String url) {
        assertThatThrownBy(() -> policy.validate(URI.create(url)))
                .isInstanceOf(ModelGatewayException.class)
                .extracting(error -> ((ModelGatewayException) error).kind())
                .isEqualTo(ModelGatewayException.Kind.UNREACHABLE);
    }
}
