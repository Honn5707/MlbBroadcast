package com.mlbbroadcast.coin;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coin_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoinTransaction {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "point_reason", nullable = false)
    private PointReason pointReason;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "bet_id", nullable = true)
    private Long betId;

    @Builder
    public CoinTransaction(Long memberId, PointReason pointReason, Integer amount,
                           LocalDateTime createdAt, Long betId) {
        this.memberId = memberId;
        this.pointReason = pointReason;
        this.amount = amount;
        this.createdAt = createdAt;
        this.betId = betId;
    }
}
