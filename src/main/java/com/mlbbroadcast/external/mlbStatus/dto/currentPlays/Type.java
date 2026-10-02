package com.mlbbroadcast.external.mlbStatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Type(

	@JsonProperty("code")
	String code,

	@JsonProperty("description")
	String description
) {
}