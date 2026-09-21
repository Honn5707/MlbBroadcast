package com.mlbbroadcast.match;

import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;


import java.time.Duration;
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
    Map<Long,ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    public void scheduleMatchStart(Matches matches){
        taskScheduler.schedule(()->startPollingForMatchStart(matches.getId()), matches.getStartedTime().atZone(ZoneId.of("UTC")).toInstant());

    }
    //스케쥴 주기는 추후 프로퍼티 설정으로 뺼꺼임
    public void startPollingForMatchStart(Long matchId){
        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(()->totalMatchService.checkMatchStart(matchId),Duration.ofSeconds(60));
        runningTasks.put(matchId, future);
    }
    //
    public void startPollingForUpdateMatch(Long matchId, Long gamePk){

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
