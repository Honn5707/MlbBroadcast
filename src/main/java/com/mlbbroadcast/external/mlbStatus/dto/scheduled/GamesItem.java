package com.mlbbroadcast.external.mlbStatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GamesItem(

	@JsonProperty("gamePk")
	Integer gamePk,

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
	Integer seriesGameNumber,

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
	Boolean reverseHomeAwayStatus,

	@JsonProperty("inningBreakLength")
	Integer inningBreakLength,

	@JsonProperty("officialDate")
	String officialDate,

	@JsonProperty("scheduledInnings")
	Integer scheduledInnings,

	@JsonProperty("publicFacing")
	Boolean publicFacing,

	@JsonProperty("dayNight")
	String dayNight,

	@JsonProperty("gamesInSeries")
	Integer gamesInSeries,

	@JsonProperty("gameNumber")
	Integer gameNumber,

	@JsonProperty("ifNecessaryDescription")
	String ifNecessaryDescription,

	@JsonProperty("seasonDisplay")
	String seasonDisplay,

	@JsonProperty("status")
	Status status
) {
}