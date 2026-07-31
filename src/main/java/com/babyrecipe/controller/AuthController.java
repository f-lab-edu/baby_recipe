package com.babyrecipe.controller;

import com.babyrecipe.config.JwtProvider;
import com.babyrecipe.dto.request.LoginRequest;
import com.babyrecipe.dto.request.PasswordResetConfirmRequest;
import com.babyrecipe.dto.request.PasswordResetRequest;
import com.babyrecipe.dto.request.RegisterRequest;
import com.babyrecipe.dto.response.ApiResponse;
import com.babyrecipe.dto.response.TokenResponse;
import com.babyrecipe.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("회원가입이 완료되었습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(required = false) String refreshToken
    ) {
        authService.logout(Long.parseLong(userDetails.getUsername()), refreshToken);
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 되었습니다."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestParam String refreshToken) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(refreshToken)));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.ok("해당 이메일로 가입된 계정이 있다면 재설정 안내를 발송했습니다."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.ok("비밀번호가 재설정되었습니다."));
    }
}
