package com.mlbbroadcast.player.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "favorite_player")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoritePlayer {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public FavoritePlayer(Long memberId, Long playerId, LocalDateTime createdAt) {
        this.memberId = memberId;
        this.playerId = playerId;
        this.createdAt = createdAt;
    }
}
