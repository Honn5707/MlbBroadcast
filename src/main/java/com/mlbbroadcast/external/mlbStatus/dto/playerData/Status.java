package com.mlbbroadcast.external.mlbStatus.dto.playerData;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Status(

	@JsonProperty("code")
	String code,

	@JsonProperty("description")
	String description
) {
}