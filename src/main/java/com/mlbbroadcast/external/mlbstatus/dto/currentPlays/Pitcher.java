package com.mlbbroadcast.external.mlbstatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Pitcher(

	@JsonProperty("fullName")
	String fullName,

	@JsonProperty("id")
	int id
) {
}