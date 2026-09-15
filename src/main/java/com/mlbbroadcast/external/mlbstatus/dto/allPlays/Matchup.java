package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Matchup(

	@JsonProperty("batter")
	Batter batter,

	@JsonProperty("pitcher")
	Pitcher pitcher
) {
}