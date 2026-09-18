package com.mlbbroadcast.external.mlbstatus.dto.lineUp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LineUpResponse(

	@JsonProperty("teams")
	Teams teams
) {
}