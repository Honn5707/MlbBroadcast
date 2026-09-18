package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Defense(

	@JsonProperty("shortstop")
	Shortstop shortstop,

	@JsonProperty("third")
	Third third,

	@JsonProperty("left")
	Left left,

	@JsonProperty("center")
	Center center,

	@JsonProperty("catcher")
	Catcher catcher,

	@JsonProperty("right")
	Right right,

	@JsonProperty("first")
	First first,

	@JsonProperty("second")
	Second second
) {
}