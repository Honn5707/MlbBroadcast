package com.mlbbroadcast.external.mlbStatus.dto.currentPlays;

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