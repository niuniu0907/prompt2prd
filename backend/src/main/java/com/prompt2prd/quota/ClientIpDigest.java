package com.prompt2prd.quota;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Converts the actual remote address into a salted digest before storage. */
@Component
public final class ClientIpDigest {

    private final byte[] salt;

    public ClientIpDigest(QuotaProperties properties) {
        salt = properties.ipDigestSalt().getBytes(StandardCharsets.UTF_8);
    }

    public String from(ServerWebExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null) {
            return digestAddress("unknown");
        }
        InetAddress address = remote.getAddress();
        String value = address == null ? remote.getHostString() : address.getHostAddress();
        return digestAddress(value);
    }

    String digestAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update((byte) 0);
            byte[] bytes = digest.digest(
                    address.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
