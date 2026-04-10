package com.example.ecommerce.shared.auth;

import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;

public enum AuthRole {
    PLATFORM_ADMIN,
    MERCHANT_ADMIN;

    public static AuthRole parse(String raw) {
        try {
            return AuthRole.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_ROLE, "invalid role");
        }
    }
}
