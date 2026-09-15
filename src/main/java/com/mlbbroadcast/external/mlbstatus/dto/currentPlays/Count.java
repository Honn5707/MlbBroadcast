package com.mlbbroadcast.external.mlbstatus.dto.currentPlays;

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