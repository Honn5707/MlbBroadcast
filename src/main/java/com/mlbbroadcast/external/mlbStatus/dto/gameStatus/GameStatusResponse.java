package com.mlbbroadcast.external.mlbStatus.dto.gameStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GameStatusResponse(

	@JsonProperty("gameData")
	GameData gameData
) {
}