package com.mlbbroadcast.external.mlbStatus.dto.scheduled;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ScheduledListResponse(

	@JsonProperty("totalGamesInProgress")
	Integer totalGamesInProgress,

	@JsonProperty("copyright")
	String copyright,

	@JsonProperty("totalItems")
	Integer totalItems,

	@JsonProperty("totalGames")
	Integer totalGames,

	@JsonProperty("totalEvents")
	Integer totalEvents,

	@JsonProperty("dates")
	List<DatesItem> dates
) {
}