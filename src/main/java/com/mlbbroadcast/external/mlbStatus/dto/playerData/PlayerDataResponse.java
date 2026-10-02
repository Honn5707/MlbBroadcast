package com.mlbbroadcast.external.mlbStatus.dto.playerData;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerDataResponse(

	@JsonProperty("roster")
	List<RosterItem> roster,

	@JsonProperty("copyright")
	String copyright,

	@JsonProperty("teamId")
	Integer teamId,

	@JsonProperty("rosterType")
	String rosterType,

	@JsonProperty("link")
	String link
) {
}