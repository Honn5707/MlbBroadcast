package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AllPlaysResponse(
        @JsonProperty("allPlays")
        List<AllPlays> allPlays
) {
}
