package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record About(

	@JsonProperty("inning")
	Integer inning,

	@JsonProperty("atBatIndex")
	int atBatIndex,

	@JsonProperty("isTopInning")
	boolean isTopInning,

	@JsonProperty("isComplete")
	boolean isComplete
) {
}