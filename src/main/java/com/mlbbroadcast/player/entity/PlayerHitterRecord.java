package com.mlbbroadcast.player.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "player_hitter_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerHitterRecord {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "player_avg", nullable = false)
    private double playerAvg;

    @Column(name = "player_obp", nullable = false)
    private double playerObp;

    @Column(name = "player_slg", nullable = false)
    private double playerSlg;

    @Column(name = "player_ops", nullable = false)
    private double playerOps;

    @Builder
    public PlayerHitterRecord(Long playerId, Long matchId, double playerAvg,
                              double playerObp, double playerSlg, double playerOps) {
        this.playerId = playerId;
        this.matchId = matchId;
        this.playerAvg = playerAvg;
        this.playerObp = playerObp;
        this.playerSlg = playerSlg;
        this.playerOps = playerOps;
    }
}
