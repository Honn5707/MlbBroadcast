package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LeagueRecord(

	@JsonProperty("wins")
	int wins,

	@JsonProperty("pct")
	String pct,

	@JsonProperty("ties")
	int ties,

	@JsonProperty("losses")
	int losses
) {
}