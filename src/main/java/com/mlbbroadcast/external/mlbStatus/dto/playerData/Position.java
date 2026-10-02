package com.mlbbroadcast.external.mlbStatus.dto.playerData;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Position(

	@JsonProperty("code")
	String code,

	@JsonProperty("name")
	String name,

	@JsonProperty("type")
	String type,

	@JsonProperty("abbreviation")
	String abbreviation
) {
}