package com.prompt2prd.model.adapter;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.prompt2prd.model.application.ModelGatewayException;

/** Validates model URLs and returns the exact addresses that the HTTP client may use. */
public final class EndpointAddressPolicy {

    public static final String PRIVATE_HOST_ALLOWLIST_ENV = "PROMPT2PRD_MODEL_PRIVATE_HOST_ALLOWLIST";

    private final Set<String> privateHostAllowlist;
    private final HostResolver resolver;

    public EndpointAddressPolicy(Set<String> privateHostAllowlist) {
        this(privateHostAllowlist, host -> List.of(InetAddress.getAllByName(host)));
    }

    EndpointAddressPolicy(Set<String> privateHostAllowlist, HostResolver resolver) {
        this.privateHostAllowlist = normalizeAllowlist(privateHostAllowlist);
        this.resolver = resolver;
    }

    public static EndpointAddressPolicy fromEnvironment() {
        String configured = System.getenv(PRIVATE_HOST_ALLOWLIST_ENV);
        Set<String> entries = configured == null || configured.isBlank()
                ? Set.of()
                : new LinkedHashSet<>(Arrays.asList(configured.split(",")));
        return new EndpointAddressPolicy(entries);
    }

    public ValidatedEndpoint validate(URI baseUrl) {
        String scheme = baseUrl.getScheme() == null ? "" : baseUrl.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw rejected("Only HTTP and HTTPS model addresses are supported");
        }
        if (baseUrl.getUserInfo() != null) {
            throw rejected("Model addresses must not contain embedded credentials");
        }
        if (baseUrl.getHost() == null || baseUrl.getHost().isBlank()) {
            throw rejected("Model address must contain a valid host");
        }
        if (baseUrl.getQuery() != null || baseUrl.getFragment() != null) {
            throw rejected("Model base address must not contain a query or fragment");
        }

        String host = normalize(baseUrl.getHost());
        List<InetAddress> addresses;
        try {
            addresses = List.copyOf(resolver.resolve(host));
        }
        catch (UnknownHostException ex) {
            throw new ModelGatewayException(ModelGatewayException.Kind.UNREACHABLE,
                    "Model host could not be resolved", ex);
        }
        if (addresses.isEmpty()) {
            throw rejected("Model host did not resolve to an address");
        }

        boolean allLoopback = addresses.stream().allMatch(InetAddress::isLoopbackAddress);
        if (scheme.equals("http") && (!allLoopback || !isExplicitLoopbackHost(host))) {
            throw rejected("HTTP is allowed only for loopback model addresses");
        }

        for (InetAddress address : addresses) {
            if (isRestricted(address) && !address.isLoopbackAddress()
                    && !isAllowlisted(host, address.getHostAddress())) {
                throw rejected("Private, link-local, and metadata model addresses require an allowlist entry");
            }
        }
        return new ValidatedEndpoint(baseUrl, host, addresses);
    }

    private boolean isAllowlisted(String host, String address) {
        return privateHostAllowlist.contains(host) || privateHostAllowlist.contains(normalize(address));
    }

    private static boolean isRestricted(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isIpv6UniqueLocal(address)
                || isCarrierGradeNat(address);
    }

    private static boolean isIpv6UniqueLocal(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 100 && second >= 64 && second <= 127;
    }

    private static boolean isExplicitLoopbackHost(String host) {
        return host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.equals("[::1]");
    }

    private static Set<String> normalizeAllowlist(Set<String> entries) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry != null && !entry.isBlank()) {
                normalized.add(normalize(entry));
            }
        }
        return Set.copyOf(normalized);
    }

    private static String normalize(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.endsWith(".") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static ModelGatewayException rejected(String message) {
        return new ModelGatewayException(ModelGatewayException.Kind.UNREACHABLE, message);
    }

    @FunctionalInterface
    interface HostResolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    public record ValidatedEndpoint(URI baseUrl, String host, List<InetAddress> addresses) {
        public ValidatedEndpoint {
            addresses = List.copyOf(addresses);
        }
    }
}
