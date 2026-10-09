package com.mlbbroadcast.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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


    //@Valid 검증 실패 (닉네임 형식 등)
    @ExceptionHandler({MethodArgumentNotValidException.class, MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e){
        log.warn("invalid request: {}", e.getMessage());
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    //Redis/DB 연결 실패 등 저장소 장애
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e){
        log.error("data access error", e);
        return toResponse(ErrorCode.STORAGE_UNAVAILABLE);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode){
        return ResponseEntity.status(errorCode.getStatus()).body(new ErrorResponse(errorCode.name(), errorCode.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handle(Exception e){
        log.error("unexpected error", e);
        return ResponseEntity.status(500).body(new ErrorResponse("UNEXPECTED_SERVER_ERROR","서버 오류가 발생하였습니다") );
    }


}


