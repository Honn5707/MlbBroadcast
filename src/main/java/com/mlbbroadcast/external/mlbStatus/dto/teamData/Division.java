package com.mlbbroadcast.external.mlbStatus.dto.teamData;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Division(

	@JsonProperty("name")
	String name,

	@JsonProperty("id")
	Integer id
) {
}