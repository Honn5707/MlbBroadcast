package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PitchData(

	@JsonProperty("endSpeed")
	Object endSpeed,

	@JsonProperty("startSpeed")
	Object startSpeed
) {
}