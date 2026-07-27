package com.babyrecipe.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
        @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    public String createAccessToken(Long userId, String email) {
        return buildToken(userId, email, accessTokenExpiry);
    }

    public String createRefreshToken(Long userId, String email) {
        return buildToken(userId, email, refreshTokenExpiry);
    }

    private String buildToken(Long userId, String email, long expiry) {
        Date now = new Date();
        return Jwts.builder()
            // iat/exp는 초 단위라 같은 초에 발급하면 토큰 문자열이 완전히 같아진다.
            // refresh_tokens.token의 UNIQUE 제약에 걸리므로 jti로 매번 다르게 만든다.
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(userId))
            .claim("email", email)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expiry))
            .signWith(secretKey)
            .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public long getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }
}
