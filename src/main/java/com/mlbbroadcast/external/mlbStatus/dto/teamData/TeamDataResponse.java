package com.mlbbroadcast.external.mlbStatus.dto.teamData;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamDataResponse(

	@JsonProperty("teams")
	List<TeamsItem> teams
) {
}