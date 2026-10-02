package com.mlbbroadcast.external.mlbStatus.dto.defenseLocation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Catcher(

	@JsonProperty("id")
	Integer id
) {
}