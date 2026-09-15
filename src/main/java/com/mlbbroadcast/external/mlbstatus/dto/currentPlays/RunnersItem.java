package com.mlbbroadcast.external.mlbstatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RunnersItem(

	@JsonProperty("details")
	Details details,

	@JsonProperty("movement")
	Movement movement
) {
}