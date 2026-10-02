package com.mlbbroadcast.external.mlbStatus.dto.currentPlays;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Movement(

	@JsonProperty("outNumber")
	Object outNumber,

	@JsonProperty("outBase")
	Object outBase,

	@JsonProperty("start")
	String start,

	@JsonProperty("isOut")
	Boolean isOut,

	@JsonProperty("end")
	String end,

	@JsonProperty("originBase")
	String originBase
) {
}