package com.example.ecommerce.shared.auth;

import com.example.ecommerce.shared.api.ApiResponse;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LocalAuthUserProvider localAuthUserProvider;
    private final JwtTokenService jwtTokenService;

    public AuthController(LocalAuthUserProvider localAuthUserProvider, JwtTokenService jwtTokenService) {
        this.localAuthUserProvider = localAuthUserProvider;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LocalAuthUser user = localAuthUserProvider.findByUsername(request.username())
            .filter(found -> found.password().equals(request.password()))
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "invalid credentials"));

        LoginResponse response = new LoginResponse(
            jwtTokenService.issueToken(user),
            "Bearer",
            jwtTokenService.expiresInSeconds(),
            new LoginResponse.UserInfo(
                user.userId(),
                user.username(),
                user.role().name(),
                user.merchantId()
            )
        );
        return ApiResponse.success(response);
    }
}
