package com.mlbbroadcast.external.mlbStatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Type(

	@JsonProperty("code")
	String code,

	@JsonProperty("description")
	String description
) {
}