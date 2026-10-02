package com.mlbbroadcast.external.mlbStatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Details(

	@JsonProperty("playIndex")
	Integer playIndex,

	@JsonProperty("responsiblePitcher")
	Object responsiblePitcher,

	@JsonProperty("earned")
	Boolean earned,

	@JsonProperty("rbi")
	Boolean rbi,

	@JsonProperty("teamUnearned")
	Boolean teamUnearned,

	@JsonProperty("eventType")
	String eventType,

	@JsonProperty("isScoringEvent")
	Boolean isScoringEvent,

	@JsonProperty("event")
	String event,

	@JsonProperty("runner")
	Runner runner,

	@JsonProperty("movementReason")
	String movementReason,

	@JsonProperty("code")
	String code,

	@JsonProperty("description")
	String description,

	@JsonProperty("isBall")
	Boolean isBall,

	@JsonProperty("isOut")
	Boolean isOut,

	@JsonProperty("type")
	Type type,

	@JsonProperty("isInPlay")
	Boolean isInPlay,

	@JsonProperty("isStrike")
	Boolean isStrike
) {
}