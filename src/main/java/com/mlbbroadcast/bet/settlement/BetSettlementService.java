package com.mlbbroadcast.bet.settlement;

import com.mlbbroadcast.bet.Bet;
import com.mlbbroadcast.bet.BetProperties;
import com.mlbbroadcast.bet.BetRepository;
import com.mlbbroadcast.bet.BetRetryWaiter;
import com.mlbbroadcast.bet.BetStatus;
import com.mlbbroadcast.bet.LockFailures;
import com.mlbbroadcast.coin.CoinTransaction;
import com.mlbbroadcast.coin.CoinTransactionRepository;
import com.mlbbroadcast.coin.CoinTransactionService;
import com.mlbbroadcast.coin.PointReason;
import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.member.Members;
import com.mlbbroadcast.member.MembersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

//경기 정산 (docs/design/Bet/BetSystemDesign.md 5장)
//- Bet 하나 = 트랜잭션 하나. 경기 전체 원자성은 보장하지 않고, Bet 단위 멱등성 + 보정 스캔으로 정합성 수렴
//- BetStatus는 조건부 UPDATE로만 변경 (영향 행 1일 때만 코인 처리 -> 이중 지급 방지), (bet_id, point_reason) UNIQUE가 마지막 방어선
@Slf4j
@Service
@RequiredArgsConstructor
public class BetSettlementService {
    static final BigDecimal BASE_ODDS = new BigDecimal("2.05");
    private static final Set<BetStatus> EVENT_TARGET = EnumSet.of(BetStatus.PENDING);
    private static final Set<BetStatus> CORRECTION_TARGET = EnumSet.of(BetStatus.PENDING, BetStatus.ERROR);

    private final MatchesRepository matchesRepository;
    private final BetRepository betRepository;
    private final BetSettlementRepository settlementRepository;
    private final MembersRepository membersRepository;
    private final CoinTransactionService coinTransactionService;
    private final CoinTransactionRepository coinTransactionRepository;
    private final BetProperties properties;
    private final BetRetryWaiter retryWaiter;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    //정산 결과. failedBetIds: 이번 실행에서 처리하지 못한 Bet (보정 스캔의 실패 횟수 집계 대상)
    public record SettlementResult(Long matchId, boolean settled, List<Long> failedBetIds) {
        static SettlementResult skipped(Long matchId){ return new SettlementResult(matchId, false, List.of()); }
    }

    //경기 결과에 따라 Bet에 적용할 처리
    private record Plan(boolean refund, BetStatus refundStatus, Side winSide, BigDecimal odds) {
        BetStatus resultOf(Bet bet){
            if(refund) return refundStatus;
            return bet.getBetSide() == winSide ? BetStatus.WON : BetStatus.LOST;
        }
        int coinOf(Bet bet, BetStatus result){
            return switch (result) {
                case WON -> payout(bet.getAmount(), odds);
                case CANCELED, NONE -> bet.getAmount();
                default -> 0;
            };
        }
    }

    //correction=false: 경기 종료 이벤트(PENDING 대상), true: 보정 스캔(PENDING, ERROR 대상)
    public SettlementResult settleMatch(Long matchId, boolean correction){
        Matches match = matchesRepository.findById(matchId).orElse(null);
        if(match == null){
            log.error("정산 대상 경기를 찾을 수 없음: matchId={}", matchId);
            return SettlementResult.skipped(matchId);
        }
        Set<BetStatus> target = correction ? CORRECTION_TARGET : EVENT_TARGET;
        List<Bet> bets = betRepository.findAllByMatchIdAndStatusIn(matchId, target);

        Plan plan = planOf(match);
        if(plan == null){
            log.warn("정산할 수 없는 경기 상태라 건너뜀: matchId={}, status={}", matchId, match.getMatchStatus());
            return SettlementResult.skipped(matchId);
        }
        if(bets.isEmpty()) return new SettlementResult(matchId, true, List.of());

        List<Long> failed = new ArrayList<>();
        for(Bet bet : bets){
            if(!settleWithRetry(bet, plan, target)) failed.add(bet.getId());
        }
        log.info("경기 정산 완료: matchId={}, 대상={}, 실패={}, correction={}", matchId, bets.size(), failed.size(), correction);
        return new SettlementResult(matchId, true, failed);
    }

    //CANCELED -> 환불(CANCELED), FINAL + NONE -> 환불(NONE), FINAL + HOME/VISIT -> 배율 확정 후 WON/LOST, 그 외 -> 정산 불가
    private Plan planOf(Matches match){
        if(match.getMatchStatus() == MatchStatus.CANCELED) return new Plan(true, BetStatus.CANCELED, null, null);
        if(match.getMatchStatus() != MatchStatus.FINAL) return null;
        Side winSide = match.getWinSide();
        if(winSide == null || winSide == Side.NONE) return new Plan(true, BetStatus.NONE, null, null);
        BetSettlement settlement = getOrCreateSettlement(match.getId(), winSide);
        return new Plan(false, null, winSide, settlement == null ? null : settlement.getOdds());
    }

    //match_id UNIQUE: 이미 있으면 기존 배율 사용. 동시에 생성되면 늦은 쪽은 UNIQUE 위반 후 기존 값을 읽음
    BetSettlement getOrCreateSettlement(Long matchId, Side winSide){
        Optional<BetSettlement> existing = settlementRepository.findByMatchId(matchId);
        if(existing.isPresent()) return existing.get();

        long total = betRepository.sumAmountByMatchId(matchId);
        if(total == 0) return null;
        long win = betRepository.sumAmountByMatchIdAndSide(matchId, winSide);
        BetSettlement settlement = BetSettlement.builder()
                .matchId(matchId).winSide(winSide).odds(odds(win, total)).winAmount(win).totalAmount(total).createdAt(now()).build();
        try {
            return transactionTemplate.execute(status -> settlementRepository.saveAndFlush(settlement));
        } catch (DataIntegrityViolationException e) {
            return settlementRepository.findByMatchId(matchId).orElseThrow(() -> e);
        }
    }

    //배율 = 2.05 - w/t (소수 4자리 버림). 범위 1.05 ~ 2.05
    static BigDecimal odds(long win, long total){
        BigDecimal ratio = BigDecimal.valueOf(win).divide(BigDecimal.valueOf(total), 10, RoundingMode.HALF_UP);
        return BASE_ODDS.subtract(ratio).setScale(4, RoundingMode.DOWN);
    }

    //지급액 = 베팅 금액 x 배율 (소수점 이하 버림)
    static int payout(int amount, BigDecimal odds){
        return BigDecimal.valueOf(amount).multiply(odds).setScale(0, RoundingMode.DOWN).intValueExact();
    }

    //락 획득 실패 시 롤백 후 난수 대기, 최대 N회. 모두 실패하면 해당 Bet만 ERROR(별도 트랜잭션)
    //락 실패가 아닌 예외(DB 장애 등)는 특정 Bet 문제가 아니므로 ERROR로 표시하지 않고 그대로 던짐 -> 경기 정산 중단, 보정 스캔에서 재처리
    private boolean settleWithRetry(Bet bet, Plan plan, Set<BetStatus> target){
        int maxAttempts = properties.getLockRetryMaxAttempts();
        for(int attempt = 1; attempt <= maxAttempts; attempt++){
            try {
                transactionTemplate.executeWithoutResult(status -> settleBet(bet, plan, target));
                return true;
            } catch (DataIntegrityViolationException e) {
                return resolveDuplicateRecord(bet, plan, target, e);
            } catch (RuntimeException e) {
                if(!LockFailures.isLockFailure(e)) throw e;
                log.warn("정산 중 회원 락 획득 실패: betId={}, memberId={}, attempt={}/{}", bet.getId(), bet.getMemberId(), attempt, maxAttempts);
                if(attempt < maxAttempts && !waitBeforeRetry()) break;
            }
        }
        markError(bet);
        return false;
    }

    //Bet 단위 트랜잭션: (코인 변동이 있으면) 회원 락 -> 조건부 UPDATE -> 영향 행 1일 때만 코인 기록/변경
    private void settleBet(Bet bet, Plan plan, Set<BetStatus> target){
        BetStatus result = plan.resultOf(bet);
        int coin = plan.coinOf(bet, result);

        //LOST처럼 코인 변동이 없으면 회원 락을 잡지 않음 (불필요한 락 경쟁 방지)
        Members member = coin > 0
                ? membersRepository.findByIdForUpdate(bet.getMemberId()).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
                : null;

        int updated = betRepository.settle(bet.getId(), result, target, now());
        if(updated == 0) return; //이미 다른 정산(이벤트/보정)이 처리함

        if(result == BetStatus.WON) coinTransactionService.rewardBet(member, bet.getId(), coin);
        else if(coin > 0) coinTransactionService.refundBet(member, bet.getId(), coin);
    }

    //(bet_id, point_reason) UNIQUE 위반: 같은 기록이 이미 존재. 기존 기록의 금액과 비교해
    //같으면 코인은 이미 처리된 것으로 보고 상태만 맞춤(코인 변경 없이), 다르면 불일치로 실패 처리
    private boolean resolveDuplicateRecord(Bet bet, Plan plan, Set<BetStatus> target, DataIntegrityViolationException e){
        BetStatus result = plan.resultOf(bet);
        PointReason reason = result == BetStatus.WON ? PointReason.BET_REWARD : PointReason.BET_REFUND;
        int expected = plan.coinOf(bet, result);
        Optional<CoinTransaction> existing = coinTransactionRepository.findByBetIdAndPointReason(bet.getId(), reason);
        if(existing.isPresent() && existing.get().getAmount() == expected){
            log.warn("이미 코인 기록이 있는 Bet: 상태만 정정합니다. betId={}, reason={}", bet.getId(), reason);
            transactionTemplate.executeWithoutResult(status -> betRepository.settle(bet.getId(), result, target, now()));
            return true;
        }
        log.error("코인 기록 불일치: betId={}, reason={}, expected={}, existing={}", bet.getId(), reason, expected,
                existing.map(CoinTransaction::getAmount).orElse(null), e);
        return false;
    }

    //에러 기록은 별도 트랜잭션 (정산 트랜잭션은 이미 롤백됨)
    private void markError(Bet bet){
        try {
            transactionTemplate.executeWithoutResult(status -> betRepository.markError(bet.getId()));
            log.error("락 획득 {}회 실패로 ERROR 처리: betId={}, memberId={}", properties.getLockRetryMaxAttempts(), bet.getId(), bet.getMemberId());
        } catch (RuntimeException e) {
            log.error("ERROR 상태 기록 실패: betId={}", bet.getId(), e);
        }
    }

    private boolean waitBeforeRetry(){
        try {
            retryWaiter.waitBeforeRetry();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private LocalDateTime now(){
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }
}
