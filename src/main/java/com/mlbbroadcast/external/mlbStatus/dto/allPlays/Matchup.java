package com.mlbbroadcast.external.mlbStatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Matchup(

	@JsonProperty("batter")
	Batter batter,

	@JsonProperty("pitcher")
	Pitcher pitcher
) {
}