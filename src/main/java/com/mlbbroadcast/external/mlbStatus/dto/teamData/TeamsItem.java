package com.mlbbroadcast.external.mlbStatus.dto.teamData;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamsItem(

	@JsonProperty("division")
	Division division,

	@JsonProperty("name")
	String name,

	@JsonProperty("season")
	Integer season,

	@JsonProperty("id")
	Integer id,

	@JsonProperty("shortName")
	String shortName
) {
}