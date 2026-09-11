package com.mlbbroadcast.match.entities;


import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.match.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "Matches")
@Getter
@Setter
@NoArgsConstructor
public class Matches {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "home_team_id", nullable = false)
    private Long homeTeamId;
    @Column(name = "visit_team_id", nullable = false)
    private Long visitTeamId;

    @Column(name = "home_team_score", nullable = false)
    private int homeTeamScore;
    @Column(name = "visit_team_score", nullable = false)
    private int visitTeamScore;

    //NONE, HOME, VISIT
    @Enumerated(EnumType.STRING)
    @Column(name = "win_side", nullable = false)
    private Side winSide;

    //BEFORE, PLAY, FINISHED
    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false)
    private MatchStatus matchStatus;

    @Column(name = "started_time", nullable = true)
    private LocalDateTime startedTime;

    @Column(name = "ended_time", nullable = true)
    private LocalDateTime endedTime;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(name = "external_id", nullable = false)
    private Long external_id;


    @Builder
    public Matches(Long homeTeamId, Long visitTeamId, int homeTeamScore, int visitTeamScore, Side winSide, MatchStatus matchStatus, LocalDateTime startedTime, LocalDateTime endedTime, int seasonYear, Long external_id){

        this.homeTeamId = homeTeamId;
        this.visitTeamId = visitTeamId;
        this.homeTeamScore = homeTeamScore;
        this.visitTeamScore = visitTeamScore;
        this.winSide = winSide;
        this.matchStatus = matchStatus;
        this.startedTime = startedTime;
        this.endedTime = endedTime;
        this.seasonYear = seasonYear;
        this.external_id = external_id;

    }


}
