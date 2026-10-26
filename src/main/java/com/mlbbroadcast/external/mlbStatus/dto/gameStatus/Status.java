package com.mlbbroadcast.external.mlbStatus.dto.gameStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Status(

	@JsonProperty("reason")
	String reason,

	@JsonProperty("abstractGameState")
	String abstractGameState,

	//Delayed, Postponed, Cancelled 등 세부 상태
	@JsonProperty("detailedState")
	String detailedState
) {
	public Status(String reason, String abstractGameState){
		this(reason, abstractGameState, null);
	}
}
