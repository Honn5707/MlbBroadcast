package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Shortstop(

	@JsonProperty("id")
	int id
) {
}