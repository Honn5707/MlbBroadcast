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
}
