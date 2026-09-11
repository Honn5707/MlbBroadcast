package com.mlbbroadcast.external.mlbstatus.dto.live;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Defense(

	@JsonProperty("pitcher")
	Pitcher pitcher
) {
}