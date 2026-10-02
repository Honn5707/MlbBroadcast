package com.mlbbroadcast.external.mlbStatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LeagueRecord(

	@JsonProperty("wins")
	Integer wins,

	@JsonProperty("pct")
	String pct,

	@JsonProperty("ties")
	Integer ties,

	@JsonProperty("losses")
	Integer losses
) {
}