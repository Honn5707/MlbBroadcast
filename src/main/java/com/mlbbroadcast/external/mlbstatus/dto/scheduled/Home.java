package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

public record Home(
	LeagueRecord leagueRecord,
	boolean splitSquad,
	Team team,
	int seriesNumber
) {
}
