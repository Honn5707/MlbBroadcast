package com.mlbbroadcast.external.mlbstatus.dto.gameStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GameStatusResponse(

	@JsonProperty("status")
	Status status
) {
}