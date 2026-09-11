package com.mlbbroadcast.external.mlbstatus.dto.scheduled;

public record Status(
	String codedGameState,
	String abstractGameCode,
	String abstractGameState,
	String detailedState,
	boolean startTimeTBD,
	String statusCode
) {
}
