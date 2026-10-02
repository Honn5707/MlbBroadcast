package com.mlbbroadcast.external.mlbStatus.dto.currentPlays;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CurrentPlay(

	@JsonProperty("runnerIndex")
	List<Integer> runnerIndex,

	@JsonProperty("pitchIndex")
	List<Integer> pitchIndex,

	@JsonProperty("count")
	Count count,

	@JsonProperty("atBatIndex")
	Integer atBatIndex,

	@JsonProperty("runners")
	List<RunnersItem> runners,

	@JsonProperty("playEvents")
	List<PlayEventsItem> playEvents,

	@JsonProperty("matchup")
	Matchup matchup
) {
}