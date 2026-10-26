package com.mlbbroadcast.team;


import com.mlbbroadcast.external.mlbStatus.retry.MlbApiRetryExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TeamScheduler {
    private final TeamService teamService;
    private final MlbApiRetryExecutor retryExecutor;

    //팀 마스터 데이터는 변동이 적은 데이터이므로 매 3월에 갱신
    @Scheduled(cron = "0 0 0 1 3 *")
    public void teamMasterUpdateScheduler(){
        retryExecutor.execute("team-sync", teamService::updateTeamMaster);
    }


}
