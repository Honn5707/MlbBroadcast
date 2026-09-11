package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

public record GamesItem(
	int gamePk,
	String gameType,
	Venue venue,
	String doubleHeader,
	String calendarEventID,
	String gameDate,
	String link,
	String tiebreaker,
	Content content,
	String season,
	int seriesGameNumber,
	String gameGuid,
	String gamedayType,
	String recordSource,
	String ifNecessary,
	String seriesDescription,
	Teams teams,
	boolean reverseHomeAwayStatus,
	int inningBreakLength,
	String officialDate,
	int scheduledInnings,
	boolean publicFacing,
	String dayNight,
	int gamesInSeries,
	int gameNumber,
	String ifNecessaryDescription,
	String seasonDisplay,
	Status status
) {
}
