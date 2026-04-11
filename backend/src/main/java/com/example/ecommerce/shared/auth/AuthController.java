package com.example.ecommerce.shared.auth;

import com.example.ecommerce.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthIdentityService authIdentityService;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthIdentityService authIdentityService, JwtTokenService jwtTokenService) {
        this.authIdentityService = authIdentityService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LocalAuthUser user = authIdentityService.authenticate(request.username(), request.password());

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
