package com.mlbbroadcast.match.entities;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "play_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayEvent {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;


    @Column(name = "match_play_log_id", nullable = false)
    private Long matchPlayLogId;


    @Column(name = "event", nullable = false)
    private String event;

    @Column(name = "pitch_index",nullable = true)
    private Integer pitchIndex;
    @Column(name ="description", nullable = true)
    private String description;


//----------------------------------------pitchType

    @Column(name = "start_speed", nullable = true)
    private Double startSpeed;

    @Column(name = "end_speed", nullable = true)
    private Double endSpeed;

    @Column(name = "pitch_type_descriptio ", nullable = true)
    private String pitchTypeDescription;

    @Builder
    public PlayEvent(Long matchPlayLogId,
                     Integer pitchIndex, String event, Double startSpeed, Double endSpeed, String description, String pitchTypeDescription) {
        this.matchPlayLogId = matchPlayLogId;
        this.event = event;
        this.description = description;
        this.pitchIndex = pitchIndex;
        this.startSpeed = startSpeed;
        this.endSpeed = endSpeed;
        this.pitchTypeDescription = pitchTypeDescription;
    }
}
