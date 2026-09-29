package com.mlbbroadcast.external.mlbstatus;



import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.external.mlbstatus.dto.allPlays.AllPlaysResponse;
import com.mlbbroadcast.external.mlbstatus.dto.currentPlays.CurrentPlayResponse;
import com.mlbbroadcast.external.mlbstatus.dto.defenseLocation.DefenseLocationResponse;
import com.mlbbroadcast.external.mlbstatus.dto.gameStatus.GameStatusResponse;
import com.mlbbroadcast.external.mlbstatus.dto.batterOrderLineUp.LineUpResponse;

import com.mlbbroadcast.external.mlbstatus.dto.scheduled.ScheduledListResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

@Component
public class MlbApiClient {
    private final RestClient restClient;
    private final DefaultProperties.CurrentPlay currentPlayProperties;
    private final DefaultProperties.PlayInfo playInfoProperties;

    public MlbApiClient(RestClient.Builder builder, DefaultProperties properties){
        this.restClient = builder.baseUrl("https://statsapi.mlb.com/api").build();
        this.currentPlayProperties = properties.getCurrentPlay();
        this.playInfoProperties = properties.getPlayInfo();
    }

    public ScheduledListResponse getListOfSchedule(LocalDate localDate){
        //스케쥴 로직 변경-> date가
        return restClient.get().uri("/v1/schedule?sportId=1&date={localDate}", localDate).retrieve().body(ScheduledListResponse.class);
    }
    public CurrentPlayResponse getCurrentPlay(int gamePk){
        return restClient.get().uri("/v1/game/{gamePk}/playByPlay?fields="+ currentPlayProperties.getCurrentPlayFields(), gamePk).retrieve().body(CurrentPlayResponse.class);
    }
    public AllPlaysResponse getAllPlays(int gamePk){
        return restClient.get().uri("/v1/game/{gamePk}/playByPlay?fields="+ currentPlayProperties.getAllPlayFields(), gamePk).retrieve().body(AllPlaysResponse.class);
    }

    public LineUpResponse getLineUp(int gamePk){
        return restClient.get().uri("/v1/game/{gamePk}/boxscore?fields=" + playInfoProperties.getLineUpFields(), gamePk).retrieve().body(LineUpResponse.class);
    }
//    s=dates,date,games,gamePk,teams,away,home,id,,status,abstractGameState
    public DefenseLocationResponse getDefenseLineUp(int gamePk){
        return restClient.get().uri("/v1/game/{gamePk}/linescore?fields="+ playInfoProperties.getDefenseLocationFields(), gamePk). retrieve().body(DefenseLocationResponse.class);
    }

    public GameStatusResponse getGameStatus(int gamePk){
        return restClient.get().uri("/v1.1/game/{gamePk}/feed/live?fields="+ playInfoProperties.getGameStateFields(), gamePk).retrieve().body(GameStatusResponse.class);
    }





}
