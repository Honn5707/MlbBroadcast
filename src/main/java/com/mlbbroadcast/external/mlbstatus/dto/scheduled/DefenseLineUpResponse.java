package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DefenseLineUpResponse(

	@JsonProperty("defense")
	Defense defense
) {
}