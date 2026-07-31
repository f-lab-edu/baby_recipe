package com.babyrecipe.service;

import com.babyrecipe.config.JwtProvider;
import com.babyrecipe.domain.PasswordResetToken;
import com.babyrecipe.domain.RefreshToken;
import com.babyrecipe.domain.User;
import com.babyrecipe.dto.request.LoginRequest;
import com.babyrecipe.dto.request.PasswordResetConfirmRequest;
import com.babyrecipe.dto.request.PasswordResetRequest;
import com.babyrecipe.dto.request.RegisterRequest;
import com.babyrecipe.dto.response.TokenResponse;
import com.babyrecipe.exception.BabyRecipeException;
import com.babyrecipe.repository.PasswordResetTokenRepository;
import com.babyrecipe.repository.RefreshTokenRepository;
import com.babyrecipe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final long PASSWORD_RESET_TOKEN_VALID_MINUTES = 30;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BabyRecipeException.conflict("이미 사용 중인 이메일입니다.");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw BabyRecipeException.conflict("이미 사용 중인 닉네임입니다.");
        }
        User user = User.builder()
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .nickname(request.getNickname())
            .build();
        userRepository.save(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> BabyRecipeException.badRequest("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw BabyRecipeException.badRequest("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail());

        // 기기별 동시 로그인을 허용하므로 기존 세션은 지우지 않는다.
        // 대신 만료된 토큰이 쌓이지 않도록 이 시점에 정리한다.
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .token(refreshToken)
            .expiresAt(LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenExpiry() / 1000))
            .build());

        return TokenResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(jwtProvider.getAccessTokenExpiry() / 1000)
            .build();
    }

    /**
     * refreshToken이 있으면 해당 세션만, 없으면 사용자의 모든 세션을 로그아웃한다.
     * 다른 사용자의 토큰을 넘겨 남의 세션을 끊을 수 없도록 소유자를 확인한다.
     */
    @Transactional
    public void logout(Long userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshTokenRepository.deleteByUserId(userId);
            return;
        }
        refreshTokenRepository.findByToken(refreshToken)
            .filter(stored -> stored.getUser().getId().equals(userId))
            .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw BabyRecipeException.badRequest("유효하지 않은 Refresh Token입니다.");
        }

        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow(() -> BabyRecipeException.badRequest("Refresh Token을 찾을 수 없습니다."));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw BabyRecipeException.badRequest("만료된 Refresh Token입니다.");
        }

        User user = stored.getUser();
        String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail());

        refreshTokenRepository.delete(stored);
        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .token(newRefreshToken)
            .expiresAt(LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenExpiry() / 1000))
            .build());

        return TokenResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .tokenType("Bearer")
            .expiresIn(jwtProvider.getAccessTokenExpiry() / 1000)
            .build();
    }

    /**
     * 존재하지 않는 이메일이어도 동일하게 처리해 계정 존재 여부가 노출되지 않도록 한다.
     * 실제 메일 발송 인프라가 없어 재설정 링크는 서버 로그로만 출력한다(개발용).
     */
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUserId(user.getId());

            String token = UUID.randomUUID().toString();
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(PASSWORD_RESET_TOKEN_VALID_MINUTES))
                .build());

            log.info("[비밀번호 재설정] {}님의 재설정 토큰: {} ({}분간 유효)",
                user.getEmail(), token, PASSWORD_RESET_TOKEN_VALID_MINUTES);
        });
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
            .orElseThrow(() -> BabyRecipeException.badRequest("유효하지 않은 재설정 토큰입니다."));

        if (resetToken.isUsed() || resetToken.isExpired()) {
            throw BabyRecipeException.badRequest("만료되었거나 이미 사용된 재설정 토큰입니다.");
        }

        User user = resetToken.getUser();
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        resetToken.markUsed();
        refreshTokenRepository.deleteByUserId(user.getId());
    }
}
