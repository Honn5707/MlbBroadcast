package com.mlbbroadcast.external.mlbStatus.dto.playerData;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RosterItem(

	@JsonProperty("person")
	Person person,

	@JsonProperty("jerseyNumber")
	String jerseyNumber,

	@JsonProperty("position")
	Position position,

	@JsonProperty("parentTeamId")
	Integer parentTeamId,

	@JsonProperty("status")
	Status status
) {
}