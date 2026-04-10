package com.example.ecommerce.shared.auth;

public final class AuthContextHolder {

    private static final ThreadLocal<AuthContext> HOLDER = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void set(AuthContext context) {
        HOLDER.set(context);
    }

    public static AuthContext getRequired() {
        AuthContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("auth context missing");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
