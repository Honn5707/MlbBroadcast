package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayEventsItem(

	@JsonProperty("pitchData")
	PitchData pitchData,

	@JsonProperty("type")
	String type,

	@JsonProperty("count")
	Count count,

	@JsonProperty("index")
	int index,

	@JsonProperty("details")
	Details details
) {
}