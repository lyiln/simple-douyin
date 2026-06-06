package com.simpledouyin.api.user.controller;

import com.simpledouyin.api.common.ApiResponse;
import com.simpledouyin.api.user.dto.MeResponse;
import com.simpledouyin.api.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.currentUser(request)));
    }
}
