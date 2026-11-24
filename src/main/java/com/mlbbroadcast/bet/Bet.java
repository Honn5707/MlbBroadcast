package com.mlbbroadcast.bet;


import com.mlbbroadcast.common.Side;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

//상태 변경은 엔티티 메서드가 아니라 BetRepository의 조건부 UPDATE로만 수행 (확인-변경 사이 다른 정산 개입 방지)
@Entity
@Table(name = "bet",
        uniqueConstraints = @UniqueConstraint(name = "uk_bet_member_match", columnNames = {"member_id", "match_id"}),
        indexes = {
                @Index(name = "idx_bet_match_id", columnList = "match_id"),
                @Index(name = "idx_bet_member_id", columnList = "member_id")
        })
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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "bet_side", nullable = false, length = 16)
    private Side betSide;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private BetStatus status;

    //보정 스캔에서 실패한 횟수 (4회 실패 시 FAILED)
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "settled_at", nullable = true)
    private LocalDateTime settledAt;

    @Builder
    public Bet(Long matchId, Long memberId, Integer amount, Side betSide, LocalDateTime createdAt) {
        this.matchId = matchId;
        this.memberId = memberId;
        this.amount = amount;
        this.betSide = betSide;
        this.status = BetStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = createdAt;
    }
}
