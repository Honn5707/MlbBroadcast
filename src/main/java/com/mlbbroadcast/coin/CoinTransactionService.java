package com.mlbbroadcast.coin;

import com.mlbbroadcast.member.Members;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

//코인 변경과 CoinTransaction 기록은 반드시 이 서비스로만 수행 (기록 없는 잔액 변경 방지)
//호출자(베팅 생성/Bet 정산)의 트랜잭션 안에서만 실행: 잔액 변경 + 기록 + Bet 상태 변경이 함께 커밋/롤백되어야 하므로 MANDATORY
//member는 호출자가 비관적 락(findByIdForUpdate)으로 조회한 엔티티여야 함
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CoinTransactionService {
    private final CoinTransactionRepository coinTransactionRepository;
    private final Clock clock;

    public void spendForBet(Members member, Long betId, int amount){
        //기록을 먼저 flush해 (bet_id, point_reason) UNIQUE 위반을 잔액 변경 전에 드러냄
        record(member, betId, PointReason.BET_PLACED, -amount);
        member.spendCoin(amount);
    }

    public void rewardBet(Members member, Long betId, int amount){
        record(member, betId, PointReason.BET_REWARD, amount);
        member.earnCoin(amount);
    }

    public void refundBet(Members member, Long betId, int amount){
        record(member, betId, PointReason.BET_REFUND, amount);
        member.earnCoin(amount);
    }

    private void record(Members member, Long betId, PointReason reason, int signedAmount){
        coinTransactionRepository.saveAndFlush(CoinTransaction.builder()
                .memberId(member.getId())
                .betId(betId)
                .pointReason(reason)
                .amount(signedAmount)
                .createdAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)))
                .build());
    }
}
