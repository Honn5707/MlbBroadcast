package com.mlbbroadcast.external.mlbstatus.dto.gameStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GameData(

	@JsonProperty("datetime")
	Datetime datetime,

	@JsonProperty("status")
	Status status
) {
}