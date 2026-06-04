package com.babyrecipe.service;

import com.babyrecipe.config.JwtProvider;
import com.babyrecipe.domain.User;
import com.babyrecipe.dto.request.LoginRequest;
import com.babyrecipe.dto.request.RegisterRequest;
import com.babyrecipe.dto.response.TokenResponse;
import com.babyrecipe.exception.BabyRecipeException;
import com.babyrecipe.repository.RefreshTokenRepository;
import com.babyrecipe.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks AuthService authService;
    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;

    @Test
    @DisplayName("회원가입 성공")
    void register_success() {
        given(userRepository.existsByEmail(any())).willReturn(false);
        given(userRepository.existsByNickname(any())).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("encoded");

        RegisterRequest request = new RegisterRequest();
        setField(request, "email", "test@test.com");
        setField(request, "password", "password123");
        setField(request, "nickname", "테스터");

        authService.register(request);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("이메일 중복 시 회원가입 실패")
    void register_duplicateEmail_throwsException() {
        given(userRepository.existsByEmail(any())).willReturn(true);

        RegisterRequest request = new RegisterRequest();
        setField(request, "email", "dup@test.com");
        setField(request, "password", "password123");
        setField(request, "nickname", "닉네임");

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(BabyRecipeException.class)
            .hasMessageContaining("이메일");
    }

    @Test
    @DisplayName("로그인 성공 후 토큰 반환")
    void login_success() {
        User user = User.builder()
            .email("test@test.com").password("encoded").nickname("테스터").build();
        setField(user, "id", 1L);

        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(any(), any())).willReturn(true);
        given(jwtProvider.createAccessToken(any(), any())).willReturn("access");
        given(jwtProvider.createRefreshToken(any(), any())).willReturn("refresh");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(604800000L);

        LoginRequest request = new LoginRequest();
        setField(request, "email", "test@test.com");
        setField(request, "password", "password123");

        TokenResponse token = authService.login(request);
        assertThat(token.getAccessToken()).isEqualTo("access");
        assertThat(token.getRefreshToken()).isEqualTo("refresh");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new RuntimeException("Field not found: " + name);
    }
}
