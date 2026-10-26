package com.mlbbroadcast.match.event;

//status 반복 조회(STATUS) 폴링으로 전환 요청. LIVE 중 지연 발생 시 사용 (MatchScheduler가 처리)
public record MatchStatusPollingEvent(Long matchId, int gamePk) {
}
