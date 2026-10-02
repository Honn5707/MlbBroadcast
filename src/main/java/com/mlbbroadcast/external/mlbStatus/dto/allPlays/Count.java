package com.mlbbroadcast.external.mlbStatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Count(

	@JsonProperty("outs")
	Integer outs,

	@JsonProperty("balls")
	Integer balls,

	@JsonProperty("strikes")
	Integer strikes
) {
}