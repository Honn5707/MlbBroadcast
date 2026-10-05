package com.mlbbroadcast.util;

import com.mlbbroadcast.configuration.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class TokenProvider {
    private final JwtProperties jwtProperties;
    public String generateJwtToken(Long memberId){
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
        String token = Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+jwtProperties.getExpireMs()))
                .signWith(key)
                .compact();

        return token;
    }
//    토큰을 받아 인가처리
    public Long validateToken(String token){
        SecretKey key= Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();


        return Long.valueOf(claims.getSubject());
    }
}
