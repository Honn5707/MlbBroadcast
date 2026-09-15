package com.mlbbroadcast.external.mlbstatus;



import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.external.mlbstatus.dto.allPlays.AllPlaysResponse;
import com.mlbbroadcast.external.mlbstatus.dto.currentPlays.CurrentPlayResponse;
import com.mlbbroadcast.external.mlbstatus.dto.scheduled.ScheduledListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class MlbApiClient {
    //경기 일정 조회
    private final RestClient restClient;
    private final DefaultProperties properties;

    public MlbApiClient(RestClient.Builder builder){
        this.restClient = builder.baseUrl("https://statsapi.mlb.com/api").build();
    }

    public ScheduledListResponse getListOfSchedule(LocalDate localDate){
        return restClient.get().uri("/v1/schedule?sportId=1&date={localDate}", localDate).retrieve().body(ScheduledListResponse.class);
    }
    public CurrentPlayResponse getCurrentPlay(Long gamePk){
        return restClient.get().uri("/v1/game/{gamePk}/playByPlay?fields="+ , gamePk).retrieve().body(CurrentPlayResponse.class);
    }
    public AllPlaysResponse getAllPlays(Long gamePk){
        return restClient.get().uri("/v1/game/{gamePk}/linescore", gamePk).retrieve().body(CurrentPlayResponse.class);
    }




}
