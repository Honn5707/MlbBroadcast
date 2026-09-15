package com.mlbbroadcast.match.entities;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "match_pitch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchPitch {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "match_playlog_id", nullable = false)
    private MatchPlaylog matchPlaylog;

    @Column(name = "pitch_index", nullable = false)
    private int pitchIndex;

    @Column(name = "call_description", nullable = true)
    private String callDescription;

    @Column(name = "start_speed", nullable = true)
    private Double startSpeed;

    @Column(name = "end_speed", nullable = true)
    private Double endSpeed;

    @Column(name = "pitch_type_code", nullable = true)
    private String pitchTypeCode;

    @Builder
    public MatchPitch(MatchPlaylog matchPlaylog, int pitchIndex, String callDescription,
                      Double startSpeed, Double endSpeed, String pitchTypeCode) {
        this.matchPlaylog = matchPlaylog;
        this.pitchIndex = pitchIndex;
        this.callDescription = callDescription;
        this.startSpeed = startSpeed;
        this.endSpeed = endSpeed;
        this.pitchTypeCode = pitchTypeCode;
    }
}
