package com.mlbbroadcast.match.service;

import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.GameStatusResponse;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.LiveData;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.Status;
import com.mlbbroadcast.match.enums.MatchStatus;

import java.util.Locale;

//MLB API status 응답을 내부 상태로 해석한 결과. 점수는 FINAL 확정 시 사용
public record MlbGameState(MatchStatus status, Integer homeRuns, Integer awayRuns) {

    //응답이 비어 있거나 해석할 수 없으면 null
    public static MlbGameState from(GameStatusResponse response){
        if(response == null || response.gameData() == null) return null;
        MatchStatus status = toMatchStatus(response.gameData().status());
        if(status == null) return null;
        LiveData.Teams teams = response.liveData() == null || response.liveData().linescore() == null
                ? null : response.liveData().linescore().teams();
        Integer home = teams == null || teams.home() == null ? null : teams.home().runs();
        Integer away = teams == null || teams.away() == null ? null : teams.away().runs();
        return new MlbGameState(status, home, away);
    }

    //abstractGameState(Preview/Live/Final)를 기본으로, detailedState로 지연/취소를 구분
    static MatchStatus toMatchStatus(Status status){
        if(status == null || status.abstractGameState() == null) return null;
        String detailed = status.detailedState() == null ? "" : status.detailedState().toLowerCase(Locale.ROOT);

        if(detailed.contains("postponed") || detailed.contains("cancelled") || detailed.contains("canceled"))
            return MatchStatus.CANCELED;
        //서스펜디드(다른 날 재개)도 지연으로 보고, 지연 한도 초과 시 MATCH_SUSPENDED_UNKNOWN으로 처리
        if(detailed.contains("delayed") || detailed.contains("suspended"))
            return MatchStatus.DELAYED;

        return switch (status.abstractGameState()) {
            case "Preview" -> MatchStatus.SCHEDULED;
            case "Live" -> MatchStatus.LIVE;
            case "Final" -> MatchStatus.FINAL;
            default -> null;
        };
    }

    public boolean hasScore(){
        return homeRuns != null && awayRuns != null;
    }
}
