package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

public record LeagueRecord(
	int wins,
	String pct,
	int ties,
	int losses
) {
}
