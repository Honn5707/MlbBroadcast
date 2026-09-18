package com.mlbbroadcast.external.mlbstatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record About(

	@JsonProperty("inning")
	int inning,

	@JsonProperty("halfInning")
	String halfInning
) {
}