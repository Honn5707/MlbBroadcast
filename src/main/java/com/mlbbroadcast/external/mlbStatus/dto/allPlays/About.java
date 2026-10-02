package com.mlbbroadcast.external.mlbStatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record About(

	@JsonProperty("inning")
	Integer inning,

	@JsonProperty("atBatIndex")
	Integer atBatIndex,

	@JsonProperty("isTopInning")
	Boolean isTopInning,

	@JsonProperty("isComplete")
	Boolean isComplete
) {
}