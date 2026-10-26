package com.mlbbroadcast.player;


import com.mlbbroadcast.external.mlbStatus.retry.MlbApiRetryExecutor;
import com.mlbbroadcast.match.polling.JobLock;
import com.mlbbroadcast.match.service.PlayerIdRemapService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class PlayerScheduler {
    private static final String PLAYER_SYNC_JOB = "player-sync";
    private static final String SEASON_STATS_JOB = "season-stats";

    private final PlayerService playerService;
    private final PlayerIdRemapService playerIdRemapService;
    private final SeasonStatService seasonStatService;
    private final JobLock jobLock;
    private final MlbApiRetryExecutor retryExecutor;
    private final Clock clock;

    //매일 00시 선수 동기화 후 null 선수 id 재매핑. 서버 구동 실행과 겹치지 않도록 JobLock으로 감쌈
    @Scheduled(cron = "0 0 0 * * *")
    public void updatePlayerMaster(){
        retryExecutor.execute(PLAYER_SYNC_JOB, () -> jobLock.runExclusively(PLAYER_SYNC_JOB, () -> {
            playerService.updatePlayerMaster();
            playerIdRemapService.remapNullPlayerIds();
        }));
    }

    //매일 시즌 누적 스탯 덮어쓰기 (실행 시각: default-properties.player-properties.stats-batch-cron)
    @Scheduled(cron = "${default-properties.player-properties.stats-batch-cron}", zone = "UTC")
    public void updateSeasonStats(){
        int season = LocalDate.now(clock.withZone(ZoneOffset.UTC)).getYear();
        retryExecutor.execute(SEASON_STATS_JOB, () -> jobLock.runExclusively(SEASON_STATS_JOB,
                () -> seasonStatService.updateSeasonStats(season)));
    }
}
