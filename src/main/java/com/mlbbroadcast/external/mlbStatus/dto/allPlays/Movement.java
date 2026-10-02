package com.mlbbroadcast.external.mlbStatus.dto.allPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Movement(

	@JsonProperty("outBase")
	String outBase,

	@JsonProperty("start")
	Object start,

	@JsonProperty("isOut")
	Boolean isOut,

	@JsonProperty("end")
	Object end,

	@JsonProperty("originBase")
	Object originBase
) {
}