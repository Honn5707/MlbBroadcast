package com.mlbbroadcast.external.mlbStatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayEventsItem(

	@JsonProperty("pitchData")
	PitchData pitchData,

	@JsonProperty("count")
	Count count,

	@JsonProperty("index")
	Integer index,

	@JsonProperty("details")
	Details details,

	@JsonProperty("type")
	String type
) {
}