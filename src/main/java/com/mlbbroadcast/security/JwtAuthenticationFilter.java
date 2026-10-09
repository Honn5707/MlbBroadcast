package com.mlbbroadcast.security;

import com.mlbbroadcast.configuration.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

//Authorization 헤더의 JWT를 검증해 SecurityContext에 인증 정보를 등록
//서블릿 필터로 중복 등록되지 않도록 빈이 아닌 SecurityConfig에서 직접 생성
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    //토큰 검증 실패 사유를 EntryPoint에 전달하기 위한 request attribute 키
    public static final String AUTH_ERROR_ATTRIBUTE = "jwtAuthenticationError";

    private final JwtAuthenticator jwtAuthenticator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = JwtAuthenticator.resolveToken(request.getHeader(HttpHeaders.AUTHORIZATION));

        if(token != null){
            try {
                SecurityContextHolder.getContext().setAuthentication(jwtAuthenticator.authenticate(token));
            } catch (BusinessException e) {
                //여기서 바로 응답하지 않고, 인증이 필요한 경로일 때만 EntryPoint가 401을 응답 (permitAll 경로는 그대로 통과)
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
            }
        }
        filterChain.doFilter(request, response);
    }
}
