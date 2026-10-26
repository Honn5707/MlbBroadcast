package com.mlbbroadcast.match.service;

import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.polling.PollingPhase;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

//경기 상태 변경은 모두 이 서비스의 트랜잭션 안에서 처리. 이벤트는 호출 측이 커밋(메서드 반환) 이후 발행
//모든 수정은 findByIdForUpdate(비관적 쓰기 락)로 조회해, 같은 경기를 여러 스레드가 동시에 수정하지 못하게 함
//(락 없이 각자 읽고 저장하면 Hibernate가 전체 컬럼을 UPDATE해서, 나중 커밋이 먼저 커밋한 변경을 옛 값으로 덮어씀)
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchStatusService {
    private final MatchesRepository matchesRepository;

    public enum Outcome { CHANGED, UNCHANGED, REJECTED, NOT_FOUND }

    public record Transition(Outcome outcome, Matches match) {
        public boolean changed(){ return outcome == Outcome.CHANGED; }
    }

    //상태 전이 + 상태별 부가 필드 처리 (LIVE: 시작시각·지연 초기화, DELAYED: 지연 시작시각)
    @Transactional
    public Transition changeStatus(Long matchId, MatchStatus next, LocalDateTime now){
        Matches match = matchesRepository.findByIdForUpdate(matchId).orElse(null);
        if(match == null) return new Transition(Outcome.NOT_FOUND, null);

        MatchStatus current = match.getMatchStatus();
        if(current != next && !current.canTransitionTo(next)){
            log.warn("허용되지 않은 상태 전이 거부: matchId={}, {} -> {}", matchId, current, next);
            return new Transition(Outcome.REJECTED, match);
        }

        if(next == MatchStatus.LIVE){
            if(match.getRealStartedTime() == null) match.matchStartTimeSet(now);
            match.clearDelay();
        }
        if(next == MatchStatus.DELAYED) match.markDelayed(now);

        if(current == next) return new Transition(Outcome.UNCHANGED, match);
        match.changeStatus(next);
        log.info("경기 상태 전이: matchId={}, {} -> {}", matchId, current, next);
        return new Transition(Outcome.CHANGED, match);
    }

    //FINAL 확정 저장: 승리팀·점수·종료시각·상태를 한 트랜잭션으로 저장. 이미 FINAL이면 변경하지 않음(FINAL 이후 불변)
    @Transactional
    public Transition finalizeMatch(Long matchId, int homeScore, int visitScore, LocalDateTime now){
        Matches match = matchesRepository.findByIdForUpdate(matchId).orElse(null);
        if(match == null) return new Transition(Outcome.NOT_FOUND, null);

        MatchStatus current = match.getMatchStatus();
        if(current == MatchStatus.FINAL) return new Transition(Outcome.UNCHANGED, match);
        if(!current.canTransitionTo(MatchStatus.FINAL)){
            log.warn("허용되지 않은 상태 전이 거부: matchId={}, {} -> FINAL", matchId, current);
            return new Transition(Outcome.REJECTED, match);
        }
        match.finalizeResult(homeScore, visitScore, now);
        log.info("경기 FINAL 확정: matchId={}, home={}, visit={}", matchId, homeScore, visitScore);
        return new Transition(Outcome.CHANGED, match);
    }

    //경기 저장 이벤트의 AFTER_COMMIT 리스너에서도 호출되므로 새 트랜잭션으로 실행
    //(REQUIRED면 이미 커밋된 기존 트랜잭션에 참여해 변경이 저장되지 않음)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPollingPhase(Long matchId, PollingPhase phase){
        matchesRepository.findByIdForUpdate(matchId).ifPresent(match -> match.recordPollingPhase(phase));
    }

    //MATCH_SUSPENDED_UNKNOWN 경기가 일정 동기화에서 다시 나타나면 시작 시각 갱신
    @Transactional
    public Matches reschedule(Long matchId, LocalDateTime scheduledStartedTime){
        Matches match = matchesRepository.findByIdForUpdate(matchId).orElseThrow();
        match.reschedule(scheduledStartedTime);
        return match;
    }
}
