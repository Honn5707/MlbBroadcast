package com.mlbbroadcast.configuration;

import com.mlbbroadcast.external.mlbStatus.dto.scheduled.Team;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties("default-properties")
@Getter@Setter
public class DefaultProperties {

    private final CurrentPlay currentPlay = new CurrentPlay();
    private final PlayInfo playInfo = new PlayInfo();
    private final TeamProperties teamProperties = new TeamProperties();
    private final PlayerProperties playerProperties = new PlayerProperties();

//    application.yml참조
    @Getter@Setter
    public static class CurrentPlay{
        private Duration currentPlayCacheDuration;
        private String currentDataKeyIndex;
        private String currentPlayFields;
        private String allPlayFields;
        //마지막 타석 갱신 이후 이 시간 동안 변화가 없으면 경기 종료 여부를 확인
        private Duration gameStatusCheckInterval;
}
    @Getter@Setter
    public static class PlayInfo{
        private String gameStateFields;
        private String lineUpFields;
        private String DefenseLocationFields;
        private String DefenseLocationIndex;
        //시작 예정 시간으로부터 이 시간이 지나도 경기가 시작되지 않으면 시작 감지 폴링 종료
        private Duration matchStartPollingLimit;

    }

    @Getter@Setter
    public static class TeamProperties{
        private String teamDataFields;

        private Integer alEastCode;
        private Integer alCentralCode;
        private Integer alWestCode;
        private Integer nlEastCode;
        private Integer nlCentralCode;
        private Integer nlWestCode;
    }

    @Getter@Setter
    public static class PlayerProperties{
        private String playerDataFields;
    }






}
