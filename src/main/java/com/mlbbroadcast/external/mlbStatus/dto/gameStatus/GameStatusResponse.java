package com.mlbbroadcast.external.mlbStatus.dto.gameStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GameStatusResponse(

	@JsonProperty("gameData")
	GameData gameData,

	@JsonProperty("liveData")
	LiveData liveData
) {
	public GameStatusResponse(GameData gameData){
		this(gameData, null);
	}
}
