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

@NoArgsConstructor
public class Matches {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "home_team_id", nullable = false)
    private Long homeTeamId;
    @Column(name = "visit_team_id", nullable = false)
    private Long visitTeamId;

    @Column(name = "home_team_score", nullable = true)
    private Integer homeTeamScore;
    @Column(name = "visit_team_score", nullable = true)
    private Integer visitTeamScore;

    //NONE, HOME, VISIT
    @Enumerated(EnumType.STRING)
    @Column(name = "win_side", nullable = true)
    private Side winSide;

    //BEFORE, PLAY, FINISHED
    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false)
    private MatchStatus matchStatus;

    @Column(name = "scheduled_started_time", nullable = false)
    private LocalDateTime scheduledStartedTime;

    @Column(name = "real_started_time", nullable = true)
    private LocalDateTime realStartedTime;

    @Column(name = "ended_time", nullable = true)
    private LocalDateTime endedTime;

    @Column(name = "season_year", nullable = false)
    private String seasonYear;



    @Column(name = "external_id", nullable = false)
    private int externalId;


    @Builder
    public Matches(Long homeTeamId, Long visitTeamId, MatchStatus matchStatus, LocalDateTime scheduledStartedTime,  String seasonYear, int externalId){

        this.homeTeamId = homeTeamId;
        this.visitTeamId = visitTeamId;
        this.matchStatus = matchStatus;
        this.scheduledStartedTime = scheduledStartedTime;
        this.seasonYear = seasonYear;
        this.externalId = externalId;

    }


    public void matchStatusChange(MatchStatus matchStatus){this.matchStatus=matchStatus;}

    public void matchStartTimeSet(LocalDateTime localDateTime){this.realStartedTime = localDateTime;}

}
