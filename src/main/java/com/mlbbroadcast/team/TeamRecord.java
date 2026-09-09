package com.mlbbroadcast.team;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "team_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamRecord {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "team_avg", nullable = false)
    private double teamAvg;

    @Column(name = "team_obp", nullable = false)
    private double teamObp;

    @Column(name = "team_slg", nullable = false)
    private double teamSlg;

    @Column(name = "team_ops", nullable = false)
    private double teamOps;

    @Column(name = "team_era", nullable = false)
    private double teamEra;

    @Column(name = "team_whip", nullable = false)
    private double teamWhip;

    @Builder
    public TeamRecord(Long teamId, Long matchId, double teamAvg, double teamObp,
                      double teamSlg, double teamOps, double teamEra, double teamWhip) {
        this.teamId = teamId;
        this.matchId = matchId;
        this.teamAvg = teamAvg;
        this.teamObp = teamObp;
        this.teamSlg = teamSlg;
        this.teamOps = teamOps;
        this.teamEra = teamEra;
        this.teamWhip = teamWhip;
    }
}
