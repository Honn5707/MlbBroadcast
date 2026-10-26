package com.mlbbroadcast.match.event;

//베팅 시스템 계약: 취소/연기 확정 시 발행
public record MatchCanceledEvent(Long matchId) {
}
