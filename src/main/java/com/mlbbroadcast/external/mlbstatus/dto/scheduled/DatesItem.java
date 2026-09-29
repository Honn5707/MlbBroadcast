package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DatesItem(

	@JsonProperty("date")
	String date,

	@JsonProperty("totalGamesInProgress")
	int totalGamesInProgress,

	@JsonProperty("totalItems")
	int totalItems,

	@JsonProperty("totalGames")
	int totalGames,

	@JsonProperty("totalEvents")
	int totalEvents,

	@JsonProperty("games")
	List<GamesItem> games
) {
}