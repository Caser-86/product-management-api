package com.example.ecommerce.shared.auth;

import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthIdentityService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthIdentityService(AuthUserRepository authUserRepository, PasswordEncoder passwordEncoder) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LocalAuthUser authenticate(String username, String password) {
        AuthUserEntity user = authUserRepository.findByUsername(username)
            .filter(AuthUserEntity::isActive)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "invalid credentials");
        }

        return new LocalAuthUser(
            user.getUsername(),
            user.getPasswordHash(),
            user.getId(),
            AuthRole.parse(user.getRole()),
            user.getMerchantId()
        );
    }
}
