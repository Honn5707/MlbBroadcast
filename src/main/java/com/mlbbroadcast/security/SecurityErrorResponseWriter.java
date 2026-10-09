package com.mlbbroadcast.security;

import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.configuration.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

//시큐리티 필터 단계의 예외는 GlobalExceptionHandler를 거치지 않으므로 같은 형식(ErrorResponse)으로 직접 응답
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {
    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(errorCode.name(), errorCode.getMessage())));
    }
}
