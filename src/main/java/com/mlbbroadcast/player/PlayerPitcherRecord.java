package com.mlbbroadcast.player;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "player_pitcher_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerPitcherRecord {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "player_era", nullable = false)
    private double playerEra;

    @Column(name = "player_whip", nullable = false)
    private double playerWhip;

    @Builder
    public PlayerPitcherRecord(Long playerId, Long matchId, double playerEra, double playerWhip) {
        this.playerId = playerId;
        this.matchId = matchId;
        this.playerEra = playerEra;
        this.playerWhip = playerWhip;
    }
}
