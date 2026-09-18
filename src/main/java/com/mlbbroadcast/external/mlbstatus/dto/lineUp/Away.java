package com.mlbbroadcast.external.mlbstatus.dto.lineUp;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Away(

	@JsonProperty("pitchers")
	List<Integer> pitchers,

	@JsonProperty("batters")
	List<Integer> batters,

	@JsonProperty("battingOrder")
	List<Integer> battingOrder,

	@JsonProperty("bullpen")
	List<Integer> bullpen
) {
}