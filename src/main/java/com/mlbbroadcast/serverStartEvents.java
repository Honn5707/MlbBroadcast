package com.mlbbroadcast;

import com.mlbbroadcast.match.MatchScheduler;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.player.PlayerScheduler;
import com.mlbbroadcast.team.TeamScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class serverStartEvents {

    private final TeamScheduler teamScheduler;
    private final PlayerScheduler playerScheduler;
    private final MatchScheduler matchScheduler;
    private final MatchesRepository matchesRepository;

    //외부 API 장애로 한 단계가 실패해도 나머지 단계(특히 경기 복구)는 진행
    @EventListener(ApplicationReadyEvent.class)
    public void startServerEvent(){
        runStep("팀 동기화", teamScheduler::teamMasterUpdateScheduler);
        runStep("선수 동기화 + 재매핑", playerScheduler::updatePlayerMaster);
        runStep("일정 동기화", matchScheduler::receiveScheduledPolling);
        runStep("경기 폴링 복구", this::restoreSchedules);
    }

    //진행 중일 수 있는 경기(SCHEDULED/LIVE/DELAYED)를 다시 등록
    //시작 시각이 지난 경기는 즉시 status를 조회해 FINAL이면 backfill 후 확정, LIVE면 갭 backfill 후 이어서 폴링 (따라잡기)
    //MATCH_SUSPENDED_UNKNOWN은 다음 일정 동기화에서 재등록
    public void restoreSchedules() {
        List<Matches> matches = matchesRepository.findAllByMatchStatusIn(EnumSet.of(MatchStatus.SCHEDULED, MatchStatus.LIVE, MatchStatus.DELAYED));
        matches.forEach(matchScheduler::scheduleMatchStart);
        log.info("경기 폴링 복구 대상: {}건", matches.size());
    }

    private void runStep(String name, Runnable step){
        try {
            step.run();
        } catch (Exception e) {
            log.error("서버 구동 작업 실패: {}", name, e);
        }
    }

}
