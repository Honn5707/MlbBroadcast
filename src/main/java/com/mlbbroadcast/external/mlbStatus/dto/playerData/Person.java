package com.mlbbroadcast.external.mlbStatus.dto.playerData;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Person(

	@JsonProperty("link")
	String link,

	@JsonProperty("fullName")
	String fullName,

	@JsonProperty("id")
	Integer id
) {
}