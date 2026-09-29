package com.mlbbroadcast.external.mlbstatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Result(

        @JsonProperty("awayScore")
        int awayScore,

        @JsonProperty("homeScore")
        int homeScore
) {
}
