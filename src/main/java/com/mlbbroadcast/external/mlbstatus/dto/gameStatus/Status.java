package com.mlbbroadcast.external.mlbstatus.dto.gameStatus;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

public record Status(

	@JsonProperty("reason")
	String reason,

	@JsonProperty("abstractGameState")
	@Nullable
	String abstractGameState
) {
}