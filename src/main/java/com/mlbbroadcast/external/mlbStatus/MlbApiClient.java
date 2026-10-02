package com.mlbbroadcast.external.mlbStatus;



import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.external.mlbStatus.dto.allPlays.AllPlaysResponse;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlay;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlayResponse;
import com.mlbbroadcast.external.mlbStatus.dto.defenseLocation.DefenseLocationResponse;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.GameStatusResponse;
import com.mlbbroadcast.external.mlbStatus.dto.batterOrderLineUp.LineUpResponse;

import com.mlbbroadcast.external.mlbStatus.dto.playerData.PlayerDataResponse;
import com.mlbbroadcast.external.mlbStatus.dto.scheduled.ScheduledListResponse;
import com.mlbbroadcast.external.mlbStatus.dto.teamData.TeamDataResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

@Component
public class MlbApiClient {
    private final RestClient restClient;
    private final DefaultProperties.CurrentPlay currentPlayProperties;
    private final DefaultProperties.PlayInfo playInfoProperties;
    private final DefaultProperties.TeamProperties teamProperties;
    private final DefaultProperties.PlayerProperties playerProperties;

    public MlbApiClient(RestClient.Builder builder, DefaultProperties properties){
        this.restClient = builder.baseUrl("https://statsapi.mlb.com/api").build();
        this.currentPlayProperties = properties.getCurrentPlay();
        this.playInfoProperties = properties.getPlayInfo();
        this.teamProperties = properties.getTeamProperties();
        this.playerProperties = properties.getPlayerProperties();
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

    public TeamDataResponse getTeamData(){
        return restClient.get().uri("/v1/teams?sportId=1&fields="+ teamProperties.getTeamDataFields()).retrieve().body(TeamDataResponse.class);
    }

    public PlayerDataResponse getPlayerData(){
        return restClient.get().uri("/v1/teams/133/roster?rosterType=40Man?fields="+ playerProperties.getPlayerDataFields()).retrieve().body(PlayerDataResponse.class);
    }





}
