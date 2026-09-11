package com.mlbbroadcast.external.mlbstatus.dto.live;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Second(

	@JsonProperty("link")
	String link,

	@JsonProperty("fullName")
	String fullName,

	@JsonProperty("id")
	int id
) {
}