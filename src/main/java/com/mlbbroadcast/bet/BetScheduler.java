package com.mlbbroadcast.bet;

import com.mlbbroadcast.bet.settlement.BetSettlementService;
import com.mlbbroadcast.match.event.MatchCanceledEvent;
import com.mlbbroadcast.match.event.MatchFinalizedEvent;
import com.mlbbroadcast.match.polling.JobLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

//경기 종료 이벤트 -> 정산, 1분 주기 -> 보정 스캔. 모두 정산 전용 스레드(betTaskExecutor)에서 실행
//(이벤트는 경기 폴링 스레드에서 발행되므로, 여기서 바로 정산하면 락 대기/재시도 대기 동안 폴링이 멈춤)
@Slf4j
@Component
public class BetScheduler {
    private static final String SCAN_JOB = "bet-scan";

    private final BetSettlementService settlementService;
    private final BetScanService scanService;
    private final JobLock jobLock;
    private final TaskExecutor betTaskExecutor;

    public BetScheduler(BetSettlementService settlementService, BetScanService scanService, JobLock jobLock,
                        @Qualifier("betTaskExecutor") TaskExecutor betTaskExecutor) {
        this.settlementService = settlementService;
        this.scanService = scanService;
        this.jobLock = jobLock;
        this.betTaskExecutor = betTaskExecutor;
    }

    //MatchFinalizedEvent는 FINAL 확정 커밋 이후 발행됨 (매치 시스템 계약)
    @EventListener
    public void onMatchFinalized(MatchFinalizedEvent event){
        betTaskExecutor.execute(() -> settle(event.matchId()));
    }

    @EventListener
    public void onMatchCanceled(MatchCanceledEvent event){
        betTaskExecutor.execute(() -> settle(event.matchId()));
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void scan(){
        betTaskExecutor.execute(() -> jobLock.runExclusively(SCAN_JOB, scanService::scan));
    }

    //예외는 로그만 남김. 처리되지 못한 Bet은 보정 스캔이 다시 처리
    private void settle(Long matchId){
        try {
            settlementService.settleMatch(matchId, false);
        } catch (RuntimeException e) {
            log.error("경기 종료 정산 실패(보정 스캔에서 재처리): matchId={}", matchId, e);
        }
    }
}
