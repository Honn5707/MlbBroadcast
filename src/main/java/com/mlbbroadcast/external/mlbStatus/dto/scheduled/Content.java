package com.mlbbroadcast.external.mlbStatus.dto.scheduled;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Content(

	@JsonProperty("link")
	String link
) {
}