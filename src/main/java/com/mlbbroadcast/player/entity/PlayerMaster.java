package com.mlbbroadcast.player.entity;


import com.mlbbroadcast.common.PlayerPosition;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_master")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerMaster {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false)
    private PlayerPosition position;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "external_id", nullable = false)
    private int externalId;

    @Builder
    public PlayerMaster(Long teamId, PlayerPosition position, String name, int externalId) {
        this.teamId = teamId;
        this.position = position;
        this.name = name;
        this.externalId = externalId;
    }
}
