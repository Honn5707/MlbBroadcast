package com.mlbbroadcast.external.mlbstatus.dto.live;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

public record LiveDataResponse(

	@JsonProperty("outs")
	int outs,

	@JsonProperty("offense")
	@Nullable
	Offense offense,

	@JsonProperty("defense")
	@Nullable
	Defense defense,

	@JsonProperty("balls")
	int balls,

	@JsonProperty("strikes")
	int strikes
) {
}