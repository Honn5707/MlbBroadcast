package com.mlbbroadcast.bet;

import com.mlbbroadcast.bet.settlement.BetSettlementService;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

//보정 스캔 (docs/design/Bet/BetSystemDesign.md 6장)
//종료 후 5분 ~ 2일 이내 경기 중 PENDING/ERROR Bet이 남은 경기를 다시 정산 (이벤트 유실, 정산 중 크래시, ERROR 베팅)
//실패한 Bet은 retryCount 증가, 4회 실패 시 FAILED. 예외는 던지지 않고 로그로 남김
@Slf4j
@Service
@RequiredArgsConstructor
public class BetScanService {
    private static final EnumSet<BetStatus> UNSETTLED = EnumSet.of(BetStatus.PENDING, BetStatus.ERROR);
    private static final EnumSet<MatchStatus> ENDED = EnumSet.of(MatchStatus.FINAL, MatchStatus.CANCELED);

    private final MatchesRepository matchesRepository;
    private final BetRepository betRepository;
    private final BetSettlementService settlementService;
    private final BetProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void scan(){
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        List<Long> matchIds;
        try {
            matchIds = matchesRepository.findIdsEndedBetween(ENDED, now.minus(properties.getScanWindow()), now.minus(properties.getScanDelayAfterEnd()));
        } catch (RuntimeException e) {
            log.error("보정 스캔 대상 조회 실패", e);
            return;
        }
        for(Long matchId : matchIds){
            try {
                if(!betRepository.existsByMatchIdAndStatusIn(matchId, UNSETTLED)) continue;
                log.info("보정 대상 경기 정산: matchId={}", matchId);
                BetSettlementService.SettlementResult result = settlementService.settleMatch(matchId, true);
                result.failedBetIds().forEach(this::recordCorrectionFailure);
            } catch (RuntimeException e) {
                log.error("보정 정산 실패: matchId={}", matchId, e);
            }
        }
    }

    //별도 트랜잭션: 실패 횟수 증가 후 한도에 도달하면 FAILED
    private void recordCorrectionFailure(Long betId){
        try {
            Integer failedNow = transactionTemplate.execute(status -> {
                betRepository.incrementRetryCount(betId);
                return betRepository.markFailedIfExceeded(betId, properties.getMaxCorrectionAttempts());
            });
            if(failedNow != null && failedNow == 1)
                log.error("보정 {}회 실패로 FAILED 처리 (수동 확인 필요): betId={}", properties.getMaxCorrectionAttempts(), betId);
        } catch (RuntimeException e) {
            log.error("보정 실패 기록 실패: betId={}", betId, e);
        }
    }
}
