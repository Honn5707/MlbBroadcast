package com.mlbbroadcast.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handle(BusinessException e){
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException occurred: {}", errorCode.name());
        return ResponseEntity.status(errorCode.getStatus()).body(new ErrorResponse(errorCode.name(), errorCode.getMessage()));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handle(Exception e){
        log.error("unexpected error", e);
        return ResponseEntity.status(500).body(new ErrorResponse("UNEXPECTED_SERVER_ERROR","서버 오류가 발생하였습니다") );
    }


}


