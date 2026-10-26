package com.mlbbroadcast.match.event;

//10초 타석 폴링(LIVE_PLAY) 시작/재개 요청 (MatchScheduler가 처리)
public record MatchStartedEvent(Long matchId, int gamePk) {
}
