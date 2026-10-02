package com.mlbbroadcast.external.mlbStatus.dto.batterOrderLineUp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Teams(

	@JsonProperty("away")
	Away away,

	@JsonProperty("home")
	Home home
) {
}