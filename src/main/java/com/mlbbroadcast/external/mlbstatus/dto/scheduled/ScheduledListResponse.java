package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ScheduledListResponse(

	@JsonProperty("totalGamesInProgress")
	int totalGamesInProgress,

	@JsonProperty("copyright")
	String copyright,

	@JsonProperty("totalItems")
	int totalItems,

	@JsonProperty("totalGames")
	int totalGames,

	@JsonProperty("totalEvents")
	int totalEvents,

	@JsonProperty("dates")
	List<DatesItem> dates
) {
}