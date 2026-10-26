package com.mlbbroadcast.match.event;

//베팅 시스템 계약: FINAL 확정(승리팀·점수 저장) 커밋 이후 발행
public record MatchFinalizedEvent(Long matchId) {
}
