package com.mlbbroadcast.external.mlbStatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PitchData(

	@JsonProperty("endSpeed")
	Object endSpeed,

	@JsonProperty("startSpeed")
	Object startSpeed
) {
}