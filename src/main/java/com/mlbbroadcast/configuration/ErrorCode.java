package com.mlbbroadcast.configuration;



import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter
public enum ErrorCode {
    EXAMPLE(HttpStatus.PAYMENT_REQUIRED, "");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
