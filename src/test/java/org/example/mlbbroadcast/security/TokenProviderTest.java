package org.example.mlbbroadcast.security;

import com.mlbbroadcast.configuration.JwtProperties;
import com.mlbbroadcast.util.TokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenProviderTest {
    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long";

    private TokenProvider provider(String secret, int expireMs) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpireMs(expireMs);
        TokenProvider tokenProvider = new TokenProvider(properties);
        ReflectionTestUtils.invokeMethod(tokenProvider, "initKey");
        return tokenProvider;
    }

    @Test
    @DisplayName("발급한 토큰을 검증하면 memberId를 돌려준다")
    void roundTrip() {
        TokenProvider tokenProvider = provider(SECRET, 60_000);

        assertThat(tokenProvider.validateToken(tokenProvider.generateJwtToken(42L))).isEqualTo(42L);
    }

    @Test
    @DisplayName("만료된 토큰은 ExpiredJwtException")
    void expired() {
        TokenProvider tokenProvider = provider(SECRET, -1_000);

        String token = tokenProvider.generateJwtToken(1L);

        assertThatThrownBy(() -> tokenProvider.validateToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 SignatureException")
    void forged() {
        String forged = Jwts.builder().subject("1").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-key-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> provider(SECRET, 60_000).validateToken(forged)).isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("secret이 없으면 서버 시작 단계에서 실패한다")
    void missingSecret() {
        assertThatThrownBy(() -> provider(null, 60_000)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("secret이 256bit 미만이면 서버 시작 단계에서 실패한다")
    void weakSecret() {
        assertThatThrownBy(() -> provider("short", 60_000)).isInstanceOf(WeakKeyException.class);
    }
}
