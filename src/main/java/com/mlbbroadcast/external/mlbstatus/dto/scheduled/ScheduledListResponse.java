package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import java.util.List;

public record ScheduledListResponse(
	Integer totalGamesInProgress,
	String copyright,
	Integer totalItems,
	Integer totalGames,
	Integer totalEvents,
	List<DatesItem> dates
) {
}