package com.mlbbroadcast.external.mlbStatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Teams(

	@JsonProperty("away")
	Away away,

	@JsonProperty("home")
	Home home
) {
}