package com.mlbbroadcast.bet.dto;

import com.mlbbroadcast.common.Side;
import jakarta.validation.constraints.NotNull;

//memberId는 요청 본문이 아니라 인증 토큰(principal)에서 가져옴
public record PlaceBetRequest(
        @NotNull Long matchId,
        @NotNull Side betSide,
        @NotNull Integer amount
) {}
