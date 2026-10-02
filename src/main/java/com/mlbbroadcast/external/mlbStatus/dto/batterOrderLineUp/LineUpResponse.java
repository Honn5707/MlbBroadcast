package com.mlbbroadcast.external.mlbStatus.dto.batterOrderLineUp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LineUpResponse(

	@JsonProperty("teams")
	Teams teams
) {
}