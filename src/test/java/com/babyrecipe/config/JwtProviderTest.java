package com.babyrecipe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET = "babyrecipe-secret-key-must-be-at-least-256-bits-long-for-hs256";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, 3600000L, 604800000L);

    @Test
    @DisplayName("같은 초에 발급해도 refresh token은 매번 다르다")
    void createRefreshToken_sameSecond_returnsDifferentTokens() {
        // iat/exp는 초 단위라 jti가 없으면 같은 초에 발급된 토큰이 완전히 동일해진다.
        // refresh_tokens.token의 UNIQUE 제약에 걸려 로그인/갱신이 실패하던 원인.
        String first = jwtProvider.createRefreshToken(1L, "test@test.com");
        String second = jwtProvider.createRefreshToken(1L, "test@test.com");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("발급한 토큰은 검증을 통과하고 userId를 복원한다")
    void createAccessToken_isValidAndCarriesUserId() {
        String token = jwtProvider.createAccessToken(42L, "test@test.com");

        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.getUserId(token)).isEqualTo(42L);
    }
}
