package com.mlbbroadcast.match;

import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.event.MatchEndedPollingEvent;
import com.mlbbroadcast.match.event.MatchScheduledSaveEvent;
import com.mlbbroadcast.match.event.MatchStartedEvent;
import com.mlbbroadcast.match.service.TotalMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;


import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchScheduler {
    private final TaskScheduler taskScheduler;
    private final TotalMatchService totalMatchService;
    //스케쥴 데이터를 저장하기 위한 필드
    private final Map<Long,ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    //경기 시작 시간 대기 중인(아직 폴링 전) 스케쥴. 같은 경기가 중복 등록되지 않도록 관리
    private final Map<Long,ScheduledFuture<?>> pendingStarts = new ConcurrentHashMap<>();

    @EventListener
    public void handleMatchStart(MatchStartedEvent event){
        startPollingForUpdateMatch(event.matchId(), event.gamePk());
    }

    @EventListener
    public void handleMatchEnded(MatchEndedPollingEvent event){
        endedPolling(event.matchId());
    }

    //경기 저장 트랜잭션이 커밋된 뒤에 스케쥴 등록 (롤백된 경기가 스케쥴되지 않도록)
    @TransactionalEventListener(fallbackExecution = true)
    public void handleMatchScheduledSave(MatchScheduledSaveEvent event){

        scheduleMatchStart(event.match());

    }


//    --------------------------------------------------------------------------
    //최초 1회 실행시 확인


    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void receiveScheduledPolling(){

        totalMatchService.updateScheduleListForMatch(LocalDate.now(ZoneId.of("UTC")));
    }

    //매치시간에 맞춰 실행
    //서버 시작 시 신규 저장 이벤트와 restoreSchedules가 같은 경기를 함께 등록하므로 중복 등록 방지
    public synchronized void scheduleMatchStart(Matches matches){
        Long matchId = matches.getId();
        if(pendingStarts.containsKey(matchId) || runningTasks.containsKey(matchId)){
            log.info("로그:[{}]이미 등록된 스케쥴입니다", matchId);
            return;
        }
        ScheduledFuture<?> future = taskScheduler.schedule(()->startPollingForMatchStart(matchId), matches.getScheduledStartedTime().atZone(ZoneId.of("UTC")).toInstant());
        pendingStarts.put(matchId, future);
        log.info("로그:[{}]스케쥴이 정상적으로 등록되었습니다", matchId);
    }
    //스케쥴 주기는 추후 프로퍼티 설정으로 뺼꺼임

    //scheduleMatchStart와 동기화하여 대기 스케쥴 제거가 등록보다 먼저 일어나지 않도록 함
    public synchronized void startPollingForMatchStart(Long matchId){
        pendingStarts.remove(matchId);
        log.info("경기 시작 감지 폴링 등록: matchId={}", matchId);
        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(()->totalMatchService.checkMatchStart(matchId),Duration.ofSeconds(60));
        replaceRunningTask(matchId, future);

    }
    public void startPollingForUpdateMatch(Long matchId, int gamePk){
        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(()->totalMatchService.fetchCurrentPlayData(matchId, gamePk),Duration.ofSeconds(10));
        replaceRunningTask(matchId, future);
    }
    public void endedPolling(Long matchId){
        cancel(pendingStarts.remove(matchId));
        cancel(runningTasks.remove(matchId));
    }

    //기존 폴링을 덮어쓸 때 이전 작업을 취소하지 않으면 참조를 잃은 채 계속 실행됨
    private void replaceRunningTask(Long matchId, ScheduledFuture<?> future){
        cancel(runningTasks.put(matchId, future));
    }

    private void cancel(ScheduledFuture<?> future){
        if(future != null) future.cancel(false);
    }


}
