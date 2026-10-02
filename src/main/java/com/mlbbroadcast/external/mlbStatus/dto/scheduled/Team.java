package com.mlbbroadcast.external.mlbStatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Team(

	@JsonProperty("name")
	String name,

	@JsonProperty("link")
	String link,

	@JsonProperty("id")
	Integer id
) {
}