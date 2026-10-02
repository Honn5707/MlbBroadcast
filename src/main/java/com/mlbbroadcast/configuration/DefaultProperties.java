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
}
    @Getter@Setter
    public static class PlayInfo{
        private String gameStateFields;
        private String lineUpFields;
        private String DefenseLocationFields;
        private String DefenseLocationIndex;

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
