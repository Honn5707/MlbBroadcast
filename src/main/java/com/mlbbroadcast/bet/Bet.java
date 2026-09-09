package com.mlbbroadcast.bet;


import com.mlbbroadcast.common.Side;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bet {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "bet_side", nullable = false)
    private Side betSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BetStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "settled_at", nullable = true)
    private LocalDateTime settledAt;

    @Builder
    public Bet(Long matchId, Long memberId, Integer amount, Side betSide,
               BetStatus status, LocalDateTime createdAt, LocalDateTime settledAt) {
        this.matchId = matchId;
        this.memberId = memberId;
        this.amount = amount;
        this.betSide = betSide;
        this.status = status;
        this.createdAt = createdAt;
        this.settledAt = settledAt;
    }
}
