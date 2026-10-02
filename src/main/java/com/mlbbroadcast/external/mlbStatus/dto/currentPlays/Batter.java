package com.mlbbroadcast.external.mlbStatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Batter(

	@JsonProperty("fullName")
	String fullName,

	@JsonProperty("id")
	Integer id
) {
}