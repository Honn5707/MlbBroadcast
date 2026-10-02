package com.mlbbroadcast.external.mlbStatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Details(

	@JsonProperty("eventType")
	String eventType,

	@JsonProperty("isScoringEvent")
	Boolean isScoringEvent,

	@JsonProperty("event")
	String event,

	@JsonProperty("runner")
	Runner runner,

	@JsonProperty("description")
	String description,

	@JsonProperty("isBall")
	Boolean isBall,

	@JsonProperty("isOut")
	Boolean isOut,

	@JsonProperty("type")
	Type type,

	@JsonProperty("isStrike")
	Boolean isStrike,

	@JsonProperty("isInPlay")
	Boolean isInPlay
) {
}