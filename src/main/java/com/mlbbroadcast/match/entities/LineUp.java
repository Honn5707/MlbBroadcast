package com.mlbbroadcast.match.entities;


import com.mlbbroadcast.common.PlayerPosition;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "LineUp")
@Getter
@Setter
@NoArgsConstructor
public class LineUp {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;
    @Column(name ="player_id", nullable = true)
    private Long playerId;

    @Column(name ="match_id")
    private Long matchId;


    @Column(name = "batting_order", nullable = true)
    private Integer battingOrder;

    @Column(name="team_id")
    private Long teamId;

    @Column(name="position")
    private Long position;

    @Column(name="external_id")
    private int externalId;

    @Builder
    public LineUp(Long playerId, Long matchId, PlayerPosition position, Integer battingOrder, Long teamId ,int externalId){

        this.playerId = playerId;
        this.matchId = matchId;
        this.battingOrder = battingOrder;
        this.externalId = externalId;
        this.teamId = teamId;
    }


}
