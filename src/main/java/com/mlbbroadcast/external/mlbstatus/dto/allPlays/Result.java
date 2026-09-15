package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Result(

	@JsonProperty("description")
	String description,

	@JsonProperty("eventType")
	String eventType,

	@JsonProperty("event")
	String event
) {
}