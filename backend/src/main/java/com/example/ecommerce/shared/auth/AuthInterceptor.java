package com.example.ecommerce.shared.auth;

import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!requiresAuth(request.getRequestURI())) {
            return true;
        }

        AuthContextHolder.set(new AuthContext(
            parseRequiredLong(request.getHeader("X-User-Id"), "X-User-Id"),
            AuthRole.parse(requiredHeader(request.getHeader("X-Role"), "X-Role")),
            parseRequiredLong(request.getHeader("X-Merchant-Id"), "X-Merchant-Id")
        ));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContextHolder.clear();
    }

    private boolean requiresAuth(String path) {
        return path.startsWith("/admin/")
            || path.startsWith("/inventory/reservations");
    }

    private String requiredHeader(String value, String headerName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED, headerName + " header is required");
        }
        return value;
    }

    private Long parseRequiredLong(String value, String headerName) {
        String raw = requiredHeader(value, headerName);
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED, headerName + " header is invalid");
        }
    }
}
