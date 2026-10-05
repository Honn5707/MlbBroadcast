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

    @Column(name = "position", nullable = false)
    private String position;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "jersey_number", nullable = true)
    private Integer jerseyNumber;


    @Column(name = "external_id", nullable = false, unique = true)
    private int externalId;

    @Builder
    public PlayerMaster(Long teamId, String position, String name, int externalId, Integer jerseyNumber) {
        this.teamId = teamId;
        this.position = position;
        this.name = name;
        this.jerseyNumber = jerseyNumber;
        this.externalId = externalId;

    }

    public void changeTeamId(Long teamId){
        this.teamId = teamId;
    }
}
