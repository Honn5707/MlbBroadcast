package com.mlbbroadcast.external.mlbstatus.dto.lineUp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Teams(

	@JsonProperty("away")
	Away away,

	@JsonProperty("home")
	Home home
) {
}