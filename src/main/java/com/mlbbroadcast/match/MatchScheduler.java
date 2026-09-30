package com.mlbbroadcast.match;

import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.event.MatchEndedPollingEvent;
import com.mlbbroadcast.match.event.MatchScheduledSaveEvent;
import com.mlbbroadcast.match.event.MatchStartedEvent;
import com.mlbbroadcast.match.service.TotalMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
public class MatchScheduler {
    private final TaskScheduler taskScheduler;
    private final TotalMatchService totalMatchService;
    //스케쥴 데이터를 저장하기 위한 필드
    private final Map<Long,ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void serverStartSchedule(){
        receiveScheduledPolling();
    }

    @EventListener
    public void handleMatchStart(MatchStartedEvent event){
        startPollingForUpdateMatch(event.matchId(), event.gamePk());
    }

    @EventListener
    public void handleMatchEnded(MatchEndedPollingEvent event){
        endedPolling(event.matchId());
    }

    @EventListener
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
    public void scheduleMatchStart(Matches matches){
        taskScheduler.schedule(()->startPollingForMatchStart(matches.getId()), matches.getScheduledStartedTime().atZone(ZoneId.of("UTC")).toInstant());
    }
    //스케쥴 주기는 추후 프로퍼티 설정으로 뺼꺼임

    public void startPollingForMatchStart(Long matchId){
        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(()->totalMatchService.checkMatchStart(matchId),Duration.ofSeconds(60));
        runningTasks.put(matchId, future);
    }
    public void startPollingForUpdateMatch(Long matchId, int gamePk){

        endedPolling(matchId);

        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(()->totalMatchService.fetchCurrentPlayData(matchId, gamePk),Duration.ofSeconds(10));

        runningTasks.put(matchId, future);
    }
    public void endedPolling(Long matchId){
        ScheduledFuture<?> future =  runningTasks.remove(matchId);
        if(future!=null)
            future.cancel(false);

    }


}
