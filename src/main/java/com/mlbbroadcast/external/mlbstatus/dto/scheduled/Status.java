package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Status(

	@JsonProperty("codedGameState")
	String codedGameState,

	@JsonProperty("abstractGameCode")
	String abstractGameCode,

	@JsonProperty("abstractGameState")
	String abstractGameState,

	@JsonProperty("detailedState")
	String detailedState,

	@JsonProperty("startTimeTBD")
	boolean startTimeTBD,

	@JsonProperty("statusCode")
	String statusCode
) {
}