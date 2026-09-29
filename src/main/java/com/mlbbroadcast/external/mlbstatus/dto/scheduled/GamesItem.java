package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GamesItem(

	@JsonProperty("gamePk")
	int gamePk,

	@JsonProperty("gameType")
	String gameType,

	@JsonProperty("venue")
	Venue venue,

	@JsonProperty("doubleHeader")
	String doubleHeader,

	@JsonProperty("calendarEventID")
	String calendarEventID,

	@JsonProperty("gameDate")
	String gameDate,

	@JsonProperty("link")
	String link,

	@JsonProperty("description")
	String description,

	@JsonProperty("tiebreaker")
	String tiebreaker,

	@JsonProperty("content")
	Content content,

	@JsonProperty("season")
	String season,

	@JsonProperty("seriesGameNumber")
	int seriesGameNumber,

	@JsonProperty("gameGuid")
	String gameGuid,

	@JsonProperty("gamedayType")
	String gamedayType,

	@JsonProperty("recordSource")
	String recordSource,

	@JsonProperty("ifNecessary")
	String ifNecessary,

	@JsonProperty("seriesDescription")
	String seriesDescription,

	@JsonProperty("teams")
	Teams teams,

	@JsonProperty("reverseHomeAwayStatus")
	boolean reverseHomeAwayStatus,

	@JsonProperty("inningBreakLength")
	int inningBreakLength,

	@JsonProperty("officialDate")
	String officialDate,

	@JsonProperty("scheduledInnings")
	int scheduledInnings,

	@JsonProperty("publicFacing")
	boolean publicFacing,

	@JsonProperty("dayNight")
	String dayNight,

	@JsonProperty("gamesInSeries")
	int gamesInSeries,

	@JsonProperty("gameNumber")
	int gameNumber,

	@JsonProperty("ifNecessaryDescription")
	String ifNecessaryDescription,

	@JsonProperty("seasonDisplay")
	String seasonDisplay,

	@JsonProperty("status")
	Status status
) {
}