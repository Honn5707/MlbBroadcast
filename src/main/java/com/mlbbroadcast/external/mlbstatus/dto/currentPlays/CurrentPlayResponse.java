package com.mlbbroadcast.external.mlbstatus.dto.currentPlays;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CurrentPlayResponse(

	@JsonProperty("runnerIndex")
	List<Integer> runnerIndex,

	@JsonProperty("pitchIndex")
	List<Integer> pitchIndex,

	@JsonProperty("count")
	Count count,

	@JsonProperty("atBatIndex")
	int atBatIndex,

	@JsonProperty("runners")
	List<RunnersItem> runners,

	@JsonProperty("playEvents")
	List<PlayEventsItem> playEvents,

	@JsonProperty("matchup")
	Matchup matchup
) {
}