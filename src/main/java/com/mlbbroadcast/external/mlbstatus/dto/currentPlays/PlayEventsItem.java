package com.mlbbroadcast.external.mlbstatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayEventsItem(

	@JsonProperty("pitchData")
	PitchData pitchData,

	@JsonProperty("count")
	Count count,

	@JsonProperty("index")
	int index,

	@JsonProperty("details")
	Details details
) {
}