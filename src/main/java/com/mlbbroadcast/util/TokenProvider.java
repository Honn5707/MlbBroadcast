package com.mlbbroadcast.util;

import com.mlbbroadcast.configuration.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class TokenProvider {
    private final JwtProperties jwtProperties;
    private SecretKey key;

    //서명 키는 서버 시작 시 한 번만 생성. secret이 없거나 짧으면(256bit 미만) 시작 단계에서 실패시킴
    //(요청마다 생성하면 설정 오류가 모든 토큰의 INVALID_TOKEN으로 가려짐)
    @PostConstruct
    void initKey(){
        String secret = jwtProperties.getSecret();
        if(secret == null || secret.isBlank()) throw new IllegalStateException("jwt.secret이 설정되지 않았습니다");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateJwtToken(Long memberId){
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+jwtProperties.getExpireMs()))
                .signWith(key)
                .compact();

    }
//    토큰을 받아 인가처리
    public Long validateToken(String token){
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();


        return Long.valueOf(claims.getSubject());
    }
}
