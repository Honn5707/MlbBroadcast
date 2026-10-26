package com.mlbbroadcast.external.mlbStatus.dto.seasonStats;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

//GET /v1/stats?stats=season&group={hitting|pitching}&season={season}&sportId=1&playerPool=ALL
public record SeasonStatsResponse(
        @JsonProperty("stats") List<StatGroup> stats
) {
    public record StatGroup(@JsonProperty("splits") List<Split> splits) {}

    public record Split(
            @JsonProperty("season") String season,
            @JsonProperty("stat") Stat stat,
            @JsonProperty("team") Ref team,
            @JsonProperty("player") Ref player
    ) {}

    //MLB API는 비율 스탯을 ".285", "3.45" 같은 문자열로 주고, 계산 불가 시 "-.--" 등을 줌
    public record Stat(
            @JsonProperty("avg") String avg,
            @JsonProperty("obp") String obp,
            @JsonProperty("slg") String slg,
            @JsonProperty("ops") String ops,
            @JsonProperty("era") String era,
            @JsonProperty("whip") String whip
    ) {}

    public record Ref(@JsonProperty("id") Integer id) {}
}
