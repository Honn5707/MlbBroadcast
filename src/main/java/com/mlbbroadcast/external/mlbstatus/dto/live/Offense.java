package com.mlbbroadcast.external.mlbstatus.dto.live;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

public record Offense(

	@JsonProperty("batter")
	@Nullable
	Batter batter,

	@JsonProperty("third")
	@Nullable
	Third third,

	@JsonProperty("first")
	@Nullable
	First first,

	@JsonProperty("second")
	@Nullable
	Second second
) {
}