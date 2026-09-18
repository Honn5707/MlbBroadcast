package com.mlbbroadcast.configuration;



import lombok.Getter;
import org.springframework.data.domain.Example;
import org.springframework.http.HttpStatus;
@Getter
public enum ErrorCode {
    PLAYER_NOT_FOUND(HttpStatus.NOT_FOUND, "선수를 찾을 수 없습니다"),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다"),
    MATCH_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "지정된 매치로그 데이터를 찾을 수 없습니다"),
    MLB_STATS_NOT_RESPONSE(HttpStatus.NOT_FOUND, "응답값을 정상적으로 불러오지 못했습니다");
    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
