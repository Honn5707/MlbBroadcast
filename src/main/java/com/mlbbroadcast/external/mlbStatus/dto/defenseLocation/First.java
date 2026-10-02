package com.mlbbroadcast.external.mlbStatus.dto.defenseLocation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record First(

	@JsonProperty("id")
	Integer id
) {
}