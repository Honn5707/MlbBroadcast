package com.mlbbroadcast.external.mlbStatus.dto.batterOrderLineUp;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Home(

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