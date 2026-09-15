package com.mlbbroadcast.match.entities;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "match_playlog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchPlaylog {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "at_bat_index", nullable = false)
    private int atBatIndex;

    @Column(name = "batter_id", nullable = false)
    private Long batterId;

    @Column(name = "pitcher_id", nullable = false)
    private Long pitcherId;

    @Column(name = "result_description", nullable = true)
    private String resultDescription;

    @Column(name = "inning", nullable = false)
    private int inning;

    @Column(name = "is_highlight", nullable = false)
    private boolean isHighlight;

    @OneToMany(mappedBy = "matchPlaylog", cascade = CascadeType.ALL)
    private List<MatchPitch> pitches = new ArrayList<>();

    @Builder
    public MatchPlaylog(Long matchId, int atBatIndex, Long batterId, Long pitcherId,
                        String resultDescription, int inning, boolean isHighlight) {
        this.matchId = matchId;
        this.atBatIndex = atBatIndex;
        this.batterId = batterId;
        this.pitcherId = pitcherId;
        this.resultDescription = resultDescription;
        this.inning = inning;
        this.isHighlight = isHighlight;
    }
}
