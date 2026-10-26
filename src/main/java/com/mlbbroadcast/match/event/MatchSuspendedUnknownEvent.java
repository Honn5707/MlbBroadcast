package com.mlbbroadcast.match.event;

//베팅 시스템 계약: 지연 한도 초과로 상태 불명 처리 시 발행
public record MatchSuspendedUnknownEvent(Long matchId) {
}
