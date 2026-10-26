package com.mlbbroadcast.match;

import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.event.MatchEndedPollingEvent;
import com.mlbbroadcast.match.event.MatchScheduledSaveEvent;
import com.mlbbroadcast.match.event.MatchStartedEvent;
import com.mlbbroadcast.match.event.MatchStatusPollingEvent;
import com.mlbbroadcast.match.polling.JobLock;
import com.mlbbroadcast.match.polling.PollingPhase;
import com.mlbbroadcast.match.polling.PollingRegistry;
import com.mlbbroadcast.match.service.MatchStatusService;
import com.mlbbroadcast.match.service.TotalMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;


import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;

//경기별 폴링 등록/전환/종료. 중복 방어는 PollingRegistry(gamePk 키)가 담당
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchScheduler {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Duration STATUS_POLLING_DELAY = Duration.ofSeconds(60);
    private static final Duration LIVE_POLLING_DELAY = Duration.ofSeconds(10);
    private static final String SCHEDULE_SYNC_JOB = "match-schedule-sync";

    private final TaskScheduler taskScheduler;
    private final TotalMatchService totalMatchService;
    private final MatchStatusService matchStatusService;
    private final PollingRegistry pollingRegistry;
    private final JobLock jobLock;
    private final Clock clock;

    @EventListener
    public void handleMatchStart(MatchStartedEvent event){
        startLivePolling(event.matchId(), event.gamePk());
    }

    @EventListener
    public void handleStatusPolling(MatchStatusPollingEvent event){
        startStatusPolling(event.matchId(), event.gamePk());
    }

    @EventListener
    public void handleMatchEnded(MatchEndedPollingEvent event){
        endPolling(event.matchId(), event.gamePk());
    }

    //경기 저장 트랜잭션이 커밋된 뒤에 스케쥴 등록 (롤백된 경기가 스케쥴되지 않도록)
    @TransactionalEventListener(fallbackExecution = true)
    public void handleMatchScheduledSave(MatchScheduledSaveEvent event){
        scheduleMatchStart(event.match());
    }


//    --------------------------------------------------------------------------

    //서버 구동 실행과 cron 실행이 겹치지 않도록 JobLock으로 감쌈
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void receiveScheduledPolling(){
        jobLock.runExclusively(SCHEDULE_SYNC_JOB,
                () -> totalMatchService.updateScheduleListForMatch(LocalDate.now(clock.withZone(UTC))));
    }

    //시작 시각이 미래면 그 시각에 status 폴링 시작, 이미 지났으면(재시작 따라잡기) 즉시 status 폴링
    //구동 + 복구 + 일일 배치가 같은 경기를 등록해도 PollingRegistry가 하나만 허용
    public boolean scheduleMatchStart(Matches matches){
        Long matchId = matches.getId();
        int gamePk = matches.getGamePk();
        Instant startAt = matches.getScheduledStartedTime().atZone(UTC).toInstant();

        boolean registered;
        PollingPhase phase;
        if(startAt.isAfter(clock.instant())){
            phase = PollingPhase.WAITING_START;
            registered = pollingRegistry.register(gamePk, phase,
                    () -> taskScheduler.schedule(() -> startStatusPolling(matchId, gamePk), startAt));
        } else {
            phase = PollingPhase.STATUS;
            registered = pollingRegistry.register(gamePk, phase, () -> statusPollingTask(matchId, gamePk));
        }

        if(!registered){
            log.info("이미 폴링이 등록된 경기라 등록 거부: matㅇㅁㅇㅇㅇchId={}, gamePk={}", matchId, gamePk);
            return false;
        }
        matchStatusService.recordPollingPhase(matchId, phase);
        log.info("경기 폴링 등록: matchId={}, gamePk={}, phase={}", matchId, gamePk, phase);
        return true;
    }

    public void startStatusPolling(Long matchId, int gamePk){
        pollingRegistry.replace(gamePk, PollingPhase.STATUS, () -> statusPollingTask(matchId, gamePk));
        matchStatusService.recordPollingPhase(matchId, PollingPhase.STATUS);
        log.info("status 폴링 시작: matchId={}, gamePk={}", matchId, gamePk);
    }

    public void startLivePolling(Long matchId, int gamePk){
        pollingRegistry.replace(gamePk, PollingPhase.LIVE_PLAY,
                () -> taskScheduler.scheduleWithFixedDelay(() -> totalMatchService.fetchCurrentPlayData(matchId, gamePk), LIVE_POLLING_DELAY));
        matchStatusService.recordPollingPhase(matchId, PollingPhase.LIVE_PLAY);
        log.info("타석 폴링 시작: matchId={}, gamePk={}", matchId, gamePk);
    }

    public void endPolling(Long matchId, int gamePk){
        pollingRegistry.cancel(gamePk);
        matchStatusService.recordPollingPhase(matchId, null);
        log.info("폴링 종료: matchId={}, gamePk={}", matchId, gamePk);
    }

    private ScheduledFuture<?> statusPollingTask(Long matchId, int gamePk){
        return taskScheduler.scheduleWithFixedDelay(() -> totalMatchService.checkMatchStatus(matchId, gamePk), STATUS_POLLING_DELAY);
    }
}
