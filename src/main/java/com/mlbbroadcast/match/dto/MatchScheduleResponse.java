package com.mlbbroadcast.match.dto;

import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

//일정 조회 응답. realStartedTime과 내부 관리용 필드(delayedSince, pollingPhase)는 제외, 내부 id 포함 (실시간 구독 matchId로 사용)
//시각은 DB의 UTC 값을 KST ISO-8601로 변환
public record MatchScheduleResponse(
        Long id,
        int gamePk,
        Long homeTeamId,
        Long visitTeamId,
        Integer homeTeamScore,
        Integer visitTeamScore,
        Side winSide,
        MatchStatus matchStatus,
        OffsetDateTime scheduledStartedTime,
        OffsetDateTime endedTime,
        String seasonYear
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static MatchScheduleResponse from(Matches match){
        return new MatchScheduleResponse(
                match.getId(),
                match.getGamePk(),
                match.getHomeTeamId(),
                match.getVisitTeamId(),
                match.getHomeTeamScore(),
                match.getVisitTeamScore(),
                match.getWinSide(),
                match.getMatchStatus(),
                toKst(match.getScheduledStartedTime()),
                toKst(match.getEndedTime()),
                match.getSeasonYear());
    }

    private static OffsetDateTime toKst(LocalDateTime utc){
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).toOffsetDateTime();
    }
}
