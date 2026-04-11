package com.example.ecommerce.support;

import com.example.ecommerce.shared.auth.AuthProperties;
import com.example.ecommerce.shared.auth.AuthRole;
import com.example.ecommerce.shared.auth.LocalAuthUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class AuthTestTokens {

    private static final String DEFAULT_SECRET = "change-me-for-local-development-only";
    private static final String DEFAULT_ISSUER = "product-management-api";

    private final AuthProperties authProperties;

    public AuthTestTokens(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String platformAdminToken() {
        return tokenFor(platformAdminUser(), Instant.now(), Instant.now().plus(60, ChronoUnit.MINUTES));
    }

    public String platformAdminToken(long userId, long merchantId) {
        return tokenFor(user("platform-admin-" + userId, userId, AuthRole.PLATFORM_ADMIN, merchantId), Instant.now(), Instant.now().plus(60, ChronoUnit.MINUTES));
    }

    public String merchantAdminToken(long userId, long merchantId) {
        return tokenFor(user("merchant-admin-" + userId, userId, AuthRole.MERCHANT_ADMIN, merchantId), Instant.now(), Instant.now().plus(60, ChronoUnit.MINUTES));
    }

    public String expiredPlatformAdminToken() {
        Instant issuedAt = Instant.now().minus(2, ChronoUnit.HOURS);
        return tokenFor(platformAdminUser(), issuedAt, issuedAt.plus(1, ChronoUnit.MINUTES));
    }

    private LocalAuthUser platformAdminUser() {
        return user("platform-admin", 9001L, AuthRole.PLATFORM_ADMIN, 2001L);
    }

    private LocalAuthUser user(String username, long userId, AuthRole role, long merchantId) {
        return new LocalAuthUser(username, username + "-secret", userId, role, merchantId);
    }

    private String tokenFor(LocalAuthUser user, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
            .issuer(resolveIssuer())
            .subject(user.username())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .claim("uid", user.userId())
            .claim("role", user.role().name())
            .claim("merchantId", user.merchantId())
            .signWith(Keys.hmacShaKeyFor(resolveSecret().getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    private String resolveSecret() {
        String secret = authProperties.getJwt().getSecret();
        return (secret == null || secret.isBlank()) ? DEFAULT_SECRET : secret;
    }

    private String resolveIssuer() {
        String issuer = authProperties.getJwt().getIssuer();
        return (issuer == null || issuer.isBlank()) ? DEFAULT_ISSUER : issuer;
    }
}
