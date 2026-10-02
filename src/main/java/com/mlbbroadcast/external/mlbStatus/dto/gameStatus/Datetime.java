package com.mlbbroadcast.external.mlbStatus.dto.gameStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Datetime(

	@JsonProperty("dateTime")
	String dateTime
) {
}