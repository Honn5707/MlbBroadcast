package com.mlbbroadcast.match.dto;


import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.match.enums.MatchStatus;

import java.time.LocalDateTime;

//매치 내용을 클라이언트에게 전송하기 위한 DTO(클라이언트가 호출하기 위한 dto
public record OpenMatchDataResponse(String homeTeam, String visitTeam, Integer homeTeamScore, Integer visitTeamScore, Side winner, MatchStatus status, LocalDateTime startTime, LocalDateTime endTime, String jsonTimes) {
}
