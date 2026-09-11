package com.mlbbroadcast.external.mlbstatus;



import com.mlbbroadcast.external.mlbstatus.dto.live.LiveDataResponse;
import com.mlbbroadcast.external.mlbstatus.dto.scheduled.ScheduledListResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

@Component
public class MlbApiClient {
    //경기 일정 조회
    private final RestClient restClient;

    public MlbApiClient(RestClient.Builder builder){
        this.restClient = builder.baseUrl("https://statsapi.mlb.com/api").build();
    }

    public ScheduledListResponse listOfSchedule(LocalDate localDate){
        return restClient.get().uri("/v1/schedule?sportId=1&date={localDate}", localDate).retrieve().body(ScheduledListResponse.class);
    }
    public LiveDataResponse LiveData(LocalDate localDate){
        return restClient.get().uri("/v1/schedule?sportId=1&date={localDate}", localDate).retrieve().body(LiveDataResponse.class);
    }



}
