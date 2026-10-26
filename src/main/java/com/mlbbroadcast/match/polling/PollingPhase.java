package com.mlbbroadcast.match.polling;

public enum PollingPhase {
    //경기 시작 시각까지 대기 (1회 예약)
    WAITING_START,
    //status만 반복 조회 (시작 감지, 지연, 재시작 따라잡기)
    STATUS,
    //10초 타석 폴링
    LIVE_PLAY
}
