package com.mlbbroadcast.member;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Members {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "provider_id", nullable = false, unique = true)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private Provider providerType;


    @Column(name = "nickname", nullable = false, unique = true)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "coin_remain", nullable = false)
    private Integer coinRemain;

    @Builder
    public Members(String providerId, Provider providerType, String nickname,
                   LocalDateTime createdAt) {
        this.providerId = providerId;
        this.providerType = providerType;
        this.nickname = nickname;
        this.createdAt = createdAt;
        coinRemain = 0;
    }

    //코인 변경은 CoinTransactionService를 통해서만 호출 (기록과 함께 변경). 회원 행은 비관적 락으로 조회된 상태여야 함
    public void spendCoin(int amount){
        if(amount <= 0) throw new IllegalArgumentException("차감 코인은 양수여야 합니다: " + amount);
        if(coinRemain < amount) throw new IllegalStateException("잔액 부족: remain=" + coinRemain + ", amount=" + amount);
        this.coinRemain -= amount;
    }

    public void earnCoin(int amount){
        if(amount <= 0) throw new IllegalArgumentException("지급 코인은 양수여야 합니다: " + amount);
        this.coinRemain = Math.addExact(this.coinRemain, amount);
    }
}
