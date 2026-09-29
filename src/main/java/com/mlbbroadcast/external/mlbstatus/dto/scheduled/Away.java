package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Away(

	@JsonProperty("leagueRecord")
	LeagueRecord leagueRecord,

	@JsonProperty("splitSquad")
	boolean splitSquad,

	@JsonProperty("team")
	Team team,

	@JsonProperty("seriesNumber")
	int seriesNumber
) {
}