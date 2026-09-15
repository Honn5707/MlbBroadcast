package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record About(

	@JsonProperty("inning")
	int inning,

	@JsonProperty("atBatIndex")
	int atBatIndex,

	@JsonProperty("halfInning")
	String halfInning,

	@JsonProperty("isComplete")
	boolean isComplete
) {
}