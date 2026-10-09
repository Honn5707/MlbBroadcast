package com.mlbbroadcast.configuration;



import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter
public enum ErrorCode {
    PLAYER_NOT_FOUND(HttpStatus.NOT_FOUND, "선수를 찾을 수 없습니다"),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "팀을 찾을 수 없습니다"),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다"),
    MATCH_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "지정된 매치로그 데이터를 찾을 수 없습니다"),
    MLB_STATS_NOT_RESPONSE(HttpStatus.NOT_FOUND, "응답값을 정상적으로 불러오지 못했습니다"),
    OAUTH_NOT_RESPONSE(HttpStatus.BAD_GATEWAY, "구글 api서버 오류가 발생하였습니다."),
    AUTH_CONFLICT(HttpStatus.CONFLICT,  "잘못된 로그인 접근입니다"),
    REGISTER_TIME_OUT(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다"),
    REGISTER_DOUBLED_NAME(HttpStatus.CONFLICT, "동일한 닉네임이 존재합니다"),
    NETWORK_ERROR(HttpStatus.GATEWAY_TIMEOUT, "네트워크 오류가 발생하였습니다"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 데이터를 처리할 수 없습니다"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "로그인 토큰이 만료되었습니다"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");
    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
