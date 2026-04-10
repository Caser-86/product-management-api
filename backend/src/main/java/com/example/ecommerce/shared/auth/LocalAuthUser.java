package com.example.ecommerce.shared.auth;

public record LocalAuthUser(
    String username,
    String password,
    Long userId,
    AuthRole role,
    Long merchantId
) {
}
