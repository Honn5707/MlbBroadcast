package com.mlbbroadcast.external.mlbstatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Details(

	@JsonProperty("eventType")
	String eventType,

	@JsonProperty("isScoringEvent")
	boolean isScoringEvent,

	@JsonProperty("event")
	String event,

	@JsonProperty("runner")
	Runner runner,

	@JsonProperty("description")
	String description,

	@JsonProperty("isBall")
	boolean isBall,

	@JsonProperty("isOut")
	boolean isOut,

	@JsonProperty("type")
	Type type,

	@JsonProperty("isStrike")
	boolean isStrike,

	@JsonProperty("isInPlay")
	boolean isInPlay
) {
}