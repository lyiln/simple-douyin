package com.simpledouyin.api.auth.controller;

import com.simpledouyin.api.auth.dto.LoginRequest;
import com.simpledouyin.api.auth.dto.LoginResponse;
import com.simpledouyin.api.auth.dto.LogoutResponse;
import com.simpledouyin.api.auth.dto.RegisterRequest;
import com.simpledouyin.api.auth.dto.RegisterResponse;
import com.simpledouyin.api.auth.service.AuthService;
import com.simpledouyin.api.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @RequestBody RegisterRequest request
    ) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout() {
        return ResponseEntity.ok(ApiResponse.success(authService.logout()));
    }
}
