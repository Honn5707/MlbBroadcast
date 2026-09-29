package com.mlbbroadcast.external.mlbstatus.dto.gameStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Status(

	@JsonProperty("reason")
	String reason,

	@JsonProperty("abstractGameState")
	String abstractGameState
) {
}