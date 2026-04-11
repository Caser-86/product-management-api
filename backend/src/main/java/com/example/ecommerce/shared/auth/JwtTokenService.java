package com.example.ecommerce.shared.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtTokenService {

    private static final String DEFAULT_SECRET = "change-me-for-local-development-only";
    private static final String DEFAULT_ISSUER = "product-management-api";
    private static final long DEFAULT_ACCESS_TOKEN_TTL_MINUTES = 60L;

    private final AuthProperties authProperties;
    private final SecretKey signingKey;

    public JwtTokenService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.signingKey = Keys.hmacShaKeyFor(resolveSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(LocalAuthUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(resolveAccessTokenTtlMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
            .issuer(resolveIssuer())
            .subject(user.username())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .claim("uid", user.userId())
            .claim("role", user.role().name())
            .claim("merchantId", user.merchantId())
            .signWith(signingKey)
            .compact();
    }

    public long expiresInSeconds() {
        return resolveAccessTokenTtlMinutes() * 60;
    }

    private String resolveSecret() {
        String secret = authProperties.getJwt().getSecret();
        return (secret == null || secret.isBlank()) ? DEFAULT_SECRET : secret;
    }

    private String resolveIssuer() {
        String issuer = authProperties.getJwt().getIssuer();
        return (issuer == null || issuer.isBlank()) ? DEFAULT_ISSUER : issuer;
    }

    private long resolveAccessTokenTtlMinutes() {
        long ttlMinutes = authProperties.getJwt().getAccessTokenTtlMinutes();
        return ttlMinutes <= 0 ? DEFAULT_ACCESS_TOKEN_TTL_MINUTES : ttlMinutes;
    }
}
