package com.mlbbroadcast.external.mlbstatus.dto.defenseLocation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Right(

	@JsonProperty("id")
	int id
) {
}