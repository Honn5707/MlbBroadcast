package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PitchData(

	@JsonProperty("endSpeed")
	Double endSpeed,

	@JsonProperty("startSpeed")
	Double startSpeed
) {
}