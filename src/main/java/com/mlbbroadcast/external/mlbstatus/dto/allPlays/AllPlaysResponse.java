package com.mlbbroadcast.external.mlbstatus.dto.allPlays;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AllPlaysResponse(

	@JsonProperty("result")
	Result result,

	@JsonProperty("runnerIndex")
	List<Integer> runnerIndex,

	@JsonProperty("pitchIndex")
	List<Integer> pitchIndex,

	@JsonProperty("about")
	About about,

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