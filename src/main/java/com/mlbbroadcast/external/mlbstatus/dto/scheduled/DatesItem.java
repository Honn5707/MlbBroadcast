package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import java.util.List;

public record DatesItem(
	String date,
	int totalGamesInProgress,
	int totalItems,
	int totalGames,
	int totalEvents,
	List<GamesItem> games,
	List<Object> events
) {
}