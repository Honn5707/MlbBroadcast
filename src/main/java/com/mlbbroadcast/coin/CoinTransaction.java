package com.mlbbroadcast.coin;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

//코인 이동 기록. amount는 부호 포함 (차감: 음수, 지급/환불: 양수)
//(bet_id, point_reason) UNIQUE: 같은 베팅의 같은 사유는 한 번만 기록 (이중 지급의 마지막 방어선). bet_id가 NULL인 기록(가입 보너스 등)은 제약 대상 아님
@Entity
@Table(name = "coin_transaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_coin_transaction_bet_reason", columnNames = {"bet_id", "point_reason"}),
        indexes = @Index(name = "idx_coin_transaction_member_id", columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoinTransaction {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "point_reason", nullable = false, length = 32)
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
