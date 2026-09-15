package com.mlbbroadcast.external.mlbstatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Movement(

	@JsonProperty("outBase")
	String outBase,

	@JsonProperty("start")
	Object start,

	@JsonProperty("isOut")
	boolean isOut,

	@JsonProperty("end")
	Object end,

	@JsonProperty("originBase")
	Object originBase
) {
}