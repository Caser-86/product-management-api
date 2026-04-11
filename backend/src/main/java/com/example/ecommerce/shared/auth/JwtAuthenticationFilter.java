package com.example.ecommerce.shared.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String DEFAULT_SECRET = "change-me-for-local-development-only";
    private static final String DEFAULT_ISSUER = "product-management-api";

    private final SecretKey signingKey;
    private final String issuer;

    public JwtAuthenticationFilter(AuthProperties authProperties) {
        this.signingKey = Keys.hmacShaKeyFor(resolveSecret(authProperties).getBytes(StandardCharsets.UTF_8));
        this.issuer = resolveIssuer(authProperties);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/auth/login".equals(path)
            || (HttpMethod.GET.matches(request.getMethod()) && "/products".equals(path))
            || "/swagger-ui.html".equals(path)
            || path.startsWith("/swagger-ui/")
            || "/v3/api-docs".equals(path)
            || path.startsWith("/v3/api-docs/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            if (!authenticateIfBearerTokenPresent(request, response)) {
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean authenticateIfBearerTokenPresent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return true;
        }
        if (!authorization.startsWith("Bearer ")) {
            writeUnauthorized(response, "invalid access token");
            return false;
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "invalid access token");
            return false;
        }

        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            if (!issuer.equals(claims.getIssuer())) {
                writeUnauthorized(response, "invalid access token");
                return false;
            }

            AuthContext authContext = new AuthContext(
                claimAsLong(claims, "uid"),
                AuthRole.parse(claims.get("role", String.class)),
                claimAsLong(claims, "merchantId")
            );
            AuthContextHolder.set(authContext);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                authContext.userId(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + authContext.role().name()))
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            writeUnauthorized(response, "invalid access token");
            return false;
        }
    }

    private Long claimAsLong(Claims claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException(name + " claim is missing");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"success\":false,\"code\":\"AUTH_UNAUTHENTICATED\",\"message\":\"" + message + "\",\"data\":null}"
        );
    }

    private String resolveSecret(AuthProperties authProperties) {
        String secret = authProperties.getJwt().getSecret();
        return (secret == null || secret.isBlank()) ? DEFAULT_SECRET : secret;
    }

    private String resolveIssuer(AuthProperties authProperties) {
        String configuredIssuer = authProperties.getJwt().getIssuer();
        return (configuredIssuer == null || configuredIssuer.isBlank()) ? DEFAULT_ISSUER : configuredIssuer;
    }
}
