package com.example.ecommerce.shared.auth;

public record AuthContext(Long userId, AuthRole role, Long merchantId) {

    public boolean isPlatformAdmin() {
        return role == AuthRole.PLATFORM_ADMIN;
    }
}
