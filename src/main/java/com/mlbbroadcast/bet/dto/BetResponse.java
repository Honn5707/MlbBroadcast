package com.mlbbroadcast.bet.dto;

import com.mlbbroadcast.bet.Bet;
import com.mlbbroadcast.bet.BetStatus;
import com.mlbbroadcast.common.Side;

public record BetResponse(Long betId, Long matchId, Side betSide, int amount, BetStatus status, int coinRemain) {
    public static BetResponse of(Bet bet, int coinRemain){
        return new BetResponse(bet.getId(), bet.getMatchId(), bet.getBetSide(), bet.getAmount(), bet.getStatus(), coinRemain);
    }
}
