package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Count(

	@JsonProperty("outs")
	int outs,

	@JsonProperty("balls")
	int balls,

	@JsonProperty("strikes")
	int strikes
) {
}