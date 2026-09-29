package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Venue(

	@JsonProperty("name")
	String name,

	@JsonProperty("link")
	String link,

	@JsonProperty("id")
	int id
) {
}