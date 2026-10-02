package com.mlbbroadcast.external.mlbStatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Matchup(

	@JsonProperty("batter")
	Batter batter,

	@JsonProperty("pitcher")
	Pitcher pitcher
) {
}