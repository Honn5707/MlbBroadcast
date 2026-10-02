package com.mlbbroadcast.external.mlbStatus.dto.gameStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record GameData(

	@JsonProperty("datetime")
	Datetime datetime,

	@JsonProperty("status")
	Status status
) {
}