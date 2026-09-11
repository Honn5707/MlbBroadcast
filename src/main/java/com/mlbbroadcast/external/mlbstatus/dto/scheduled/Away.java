package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

public record Away(
	LeagueRecord leagueRecord,
	boolean splitSquad,
	Team team,
	Integer seriesNumber
) {
}
