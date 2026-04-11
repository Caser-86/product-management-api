package com.example.ecommerce.shared.auth;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    UserInfo user
) {
    public record UserInfo(
        Long userId,
        String username,
        String role,
        Long merchantId
    ) {
    }
}
