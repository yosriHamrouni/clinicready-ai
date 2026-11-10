package com.clinicready.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. The secret comes from the JWT_SECRET environment variable and must be at least
 * 32 bytes (HS256). The application refuses to start without it.
 */
@ConfigurationProperties(prefix = "clinicready.security.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer, boolean cookieSecure) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "clinicready.security.jwt.secret (JWT_SECRET) must be set to at least "
                            + MIN_SECRET_BYTES + " bytes");
        }
        if (ttl == null) {
            ttl = Duration.ofHours(8);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "clinicready";
        }
    }

    byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
