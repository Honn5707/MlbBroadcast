package com.mlbbroadcast.match.event;

//폴링 종료 요청 (MatchScheduler가 처리)
public record MatchEndedPollingEvent(Long matchId, int gamePk) {
}
