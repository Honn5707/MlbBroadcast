package com.mlbbroadcast.match.entities;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "match_play_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchplayLog {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;



    @Column(name = "at_bat_index", nullable = false)
    private Integer atBatIndex;

    @Column(name = "batter_id", nullable = true)
    private Long batterId;

    @Column(name = "pitcher_id", nullable = true)
    private Long pitcherId;

    @Column(name = "batter_id", nullable = false)
    private int batterExternalId;

    @Column(name = "pitcher_id", nullable = false)
    private int pitcherExternalId;

    @Column(name = "result_description", nullable = true)
    private String resultDescription;

    @Column(name = "inning", nullable = false)
    private int inning;


    @Builder
    public MatchplayLog(Long matchId, Integer atBatIndex, Long batterId, Long pitcherId, int batterExternalId, int pitcherExternalId,
                        String resultDescription, int inning) {
        this.matchId = matchId;
        this.atBatIndex = atBatIndex;
        this.batterId = batterId;
        this.pitcherId = pitcherId;
        this.batterExternalId = batterExternalId;
        this.pitcherExternalId = pitcherExternalId;
        this.resultDescription = resultDescription;
        this.inning = inning;
    }
}
