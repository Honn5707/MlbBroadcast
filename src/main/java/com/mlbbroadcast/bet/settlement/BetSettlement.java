package com.mlbbroadcast.bet.settlement;

import com.mlbbroadcast.common.Side;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//경기별 정산식 결과. match_id UNIQUE: 한 번 계산된 배율은 다시 계산하지 않음
@Entity
@Table(name = "bet_settlement",
        uniqueConstraints = @UniqueConstraint(name = "uk_bet_settlement_match", columnNames = "match_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BetSettlement {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "win_side", nullable = false, length = 16)
    private Side winSide;

    //2.05 - w/t (소수 4자리)
    @Column(name = "odds", nullable = false, precision = 6, scale = 4)
    private BigDecimal odds;

    //계산 근거 기록 (w: 승리 측 금액 합, t: 전체 금액 합)
    @Column(name = "win_amount", nullable = false)
    private long winAmount;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public BetSettlement(Long matchId, Side winSide, BigDecimal odds, long winAmount, long totalAmount, LocalDateTime createdAt) {
        this.matchId = matchId;
        this.winSide = winSide;
        this.odds = odds;
        this.winAmount = winAmount;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }
}
