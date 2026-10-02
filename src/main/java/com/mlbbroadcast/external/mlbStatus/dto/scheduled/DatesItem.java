package com.mlbbroadcast.external.mlbStatus.dto.scheduled;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DatesItem(

	@JsonProperty("date")
	String date,

	@JsonProperty("totalGamesInProgress")
	Integer totalGamesInProgress,

	@JsonProperty("totalItems")
	Integer totalItems,

	@JsonProperty("totalGames")
	Integer totalGames,

	@JsonProperty("totalEvents")
	Integer totalEvents,

	@JsonProperty("games")
	List<GamesItem> games
) {
}