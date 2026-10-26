package com.mlbbroadcast.external.mlbStatus.dto.gameStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LiveData(
	@JsonProperty("linescore")
	Linescore linescore
) {
	public record Linescore(@JsonProperty("teams") Teams teams) {}

	public record Teams(@JsonProperty("home") TeamScore home, @JsonProperty("away") TeamScore away) {}

	public record TeamScore(@JsonProperty("runs") Integer runs) {}
}
