package com.mlbbroadcast.external.mlbStatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Away(

	@JsonProperty("leagueRecord")
	LeagueRecord leagueRecord,

	@JsonProperty("splitSquad")
	Boolean splitSquad,

	@JsonProperty("team")
	Team team,

	@JsonProperty("seriesNumber")
	Integer seriesNumber
) {
}