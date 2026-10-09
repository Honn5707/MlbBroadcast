package com.mlbbroadcast.security;

import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.util.TokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

//HTTP 필터와 STOMP 인터셉터가 공통으로 사용하는 JWT 인증 로직
@Component
@RequiredArgsConstructor
public class JwtAuthenticator {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<SimpleGrantedAuthority> USER_AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final TokenProvider tokenProvider;

    //"Bearer {token}" 형식에서 토큰만 추출. 형식이 아니면 null
    public static String resolveToken(String authorizationHeader){
        if(authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) return null;
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    //principal은 memberId(Long). 컨트롤러에서 @AuthenticationPrincipal Long memberId로 꺼낼 수 있음
    public Authentication authenticate(String token){
        try {
            Long memberId = tokenProvider.validateToken(token);
            return UsernamePasswordAuthenticationToken.authenticated(memberId, null, USER_AUTHORITIES);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            //서명 불일치, 형식 오류, subject가 숫자가 아닌 경우 등
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }
}
