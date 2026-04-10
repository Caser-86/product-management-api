package com.example.ecommerce.shared.auth;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LocalAuthUserProvider {

    private final AuthProperties authProperties;

    public LocalAuthUserProvider(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public Optional<LocalAuthUser> findByUsername(String username) {
        return authProperties.getUsers().stream()
            .filter(user -> user.getUsername().equals(username))
            .findFirst()
            .map(user -> new LocalAuthUser(
                user.getUsername(),
                user.getPassword(),
                user.getUserId(),
                user.getRole(),
                user.getMerchantId()
            ));
    }
}
