package org.example.mlbbroadcast.security;

import com.mlbbroadcast.member.MeController;
import com.mlbbroadcast.security.JwtAccessDeniedHandler;
import com.mlbbroadcast.security.JwtAuthenticationEntryPoint;
import com.mlbbroadcast.security.JwtAuthenticator;
import com.mlbbroadcast.security.SecurityConfig;
import com.mlbbroadcast.security.SecurityErrorResponseWriter;
import com.mlbbroadcast.util.TokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//실제 SecurityFilterChain에 JwtAuthenticationFilter를 태워 검증
@WebMvcTest
@ContextConfiguration(classes = {
        SecurityConfig.class, JwtAuthenticator.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, SecurityErrorResponseWriter.class,
        MeController.class, SecurityConfigTest.TestController.class})
class SecurityConfigTest {
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    TokenProvider tokenProvider;

    @RestController
    static class TestController {
        @GetMapping("/api/auth/ping")
        String publicPing() {
            return "pong";
        }

        @GetMapping("/api/matches")
        String otherPath() {
            return "matches";
        }
    }

    @Test
    @DisplayName("유효한 토큰이면 보호된 API에 접근하고 principal로 memberId를 받는다")
    void validToken() throws Exception {
        when(tokenProvider.validateToken("valid")).thenReturn(7L);

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    @DisplayName("토큰 없이 보호된 API에 접근하면 401 AUTHENTICATION_REQUIRED")
    void noToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("만료된 토큰이면 401 EXPIRED_TOKEN")
    void expiredToken() throws Exception {
        when(tokenProvider.validateToken("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("EXPIRED_TOKEN"));
    }

    @Test
    @DisplayName("서명이 위조된 토큰이면 401 INVALID_TOKEN")
    void forgedToken() throws Exception {
        when(tokenProvider.validateToken("forged")).thenThrow(new SignatureException("bad signature"));

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("보호 경로가 아니면(/api/auth/**) 토큰 없이, 잘못된 토큰이 있어도 접근할 수 있다")
    void publicPath() throws Exception {
        when(tokenProvider.validateToken("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        mockMvc.perform(get("/api/auth/ping"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/ping").header("Authorization", "Bearer expired"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/api/me 외의 경로는 아직 보호하지 않는다 (정책 확정 전 전체 개방)")
    void otherPathsAreOpen() throws Exception {
        mockMvc.perform(get("/api/matches"))
                .andExpect(status().isOk());
    }
}
