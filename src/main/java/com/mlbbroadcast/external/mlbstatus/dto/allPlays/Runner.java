package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Runner(

	@JsonProperty("fullName")
	String fullName,

	@JsonProperty("id")
	int id
) {
}