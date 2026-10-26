package com.mlbbroadcast.match.service;


import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlay;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlayResponse;
import com.mlbbroadcast.external.mlbStatus.retry.MlbApiRetryExecutor;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.event.MatchCanceledEvent;
import com.mlbbroadcast.match.event.MatchEndedPollingEvent;
import com.mlbbroadcast.match.event.MatchFinalizedEvent;
import com.mlbbroadcast.match.event.MatchStartedEvent;
import com.mlbbroadcast.match.event.MatchStatusPollingEvent;
import com.mlbbroadcast.match.event.MatchSuspendedUnknownEvent;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//경기 폴링 흐름 담당 (docs/design/MatchSystem 5, 6장)
//외부 API 호출이 많아 트랜잭션을 걸지 않고, 상태 변경은 MatchStatusService 트랜잭션에서 처리 후 이벤트 발행
@Slf4j
@Service
@RequiredArgsConstructor
public class TotalMatchService {
    private static final String LIVE_DESTINATION_PREFIX = "/topic/matches/";

    private final MlbApiClient mlbApiClient;
    private final DefaultProperties properties;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchesRepository matchesRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SaveMatchService saveMatchService;
    private final MatchStatusService matchStatusService;
    private final MlbApiRetryExecutor retryExecutor;
    private final Clock clock;

    //10초 폴링 캐시 (메모리, 재시작 시 유실되어도 됨). 마지막으로 내용이 바뀐 시각을 함께 보관
    private record CachedPlay(CurrentPlay play, Instant changedAt) {}
    private final Map<Long, CachedPlay> currentPlayCache = new ConcurrentHashMap<>();


    //날짜별로 재시도 정책 적용. 하루가 실패해도 나머지 날짜는 계속 저장
    //MLB 일정 API의 date는 미국 현지 날짜(officialDate)라, UTC 오늘 새벽에 시작하는 경기는 미국 기준 전날 일정에 들어 있음
    //(예: 2026-10-12T00:00Z 경기 = officialDate 2026-10-11) -> 전날부터 조회해야 누락되지 않음
    public void updateScheduleListForMatch(LocalDate utcToday){
        for(int i = -1; i < 14; i++) {
            LocalDate targetDate = utcToday.plusDays(i);
            retryExecutor.execute("schedule-sync:" + targetDate, () -> saveMatchService.saveScheduledMatches(targetDate));
        }
    }

    //STATUS 폴링: 시작 감지, 지연 대기, 재시작 따라잡기에서 공통으로 사용
    public void checkMatchStatus(Long matchId, int gamePk){
        Matches match = matchesRepository.findById(matchId).orElse(null);
        if(match == null){
            log.error("경기를 찾을 수 없어 폴링을 종료합니다: matchId={}", matchId);
            eventPublisher.publishEvent(new MatchEndedPollingEvent(matchId, gamePk));
            return;
        }
        if(match.getMatchStatus().isTerminal()){
            endPolling(match);
            return;
        }

        MlbGameState state = fetchGameState(match.getGamePk());
        if(state == null){
            log.error("경기 상태를 정상적으로 받아오지 못했습니다: gamePk={}", match.getGamePk());
            return;
        }

        switch (state.status()){
            case LIVE -> onLive(match);
            case DELAYED -> onDelayed(match, false);
            case FINAL -> finalizeMatch(match, state);
            case CANCELED -> cancelMatch(match);
            //시작 전 상태가 계속되면 일정 한도까지만 대기
            default -> stopIfStartPollingExpired(match);
        }
    }


    //  실시간 타석 정보 데이터를 외부 API로부터 받아오는 메서드 (external.mlbstatus 디렉토리 참고)
    // 해당 로직은 스케쥴 경기시간동안 스케쥴러가 10초마다 폴링. ->타석 종료 시, 타석데이터를 덮어씀
    public void fetchCurrentPlayData(Long matchId, int gamePk){
        CurrentPlayResponse currentPlayResponse = mlbApiClient.getCurrentPlay(gamePk);
        CurrentPlay response = currentPlayResponse == null ? null : currentPlayResponse.currentPlay();

        if(response == null || response.atBatIndex() == null){
            log.info("타석데이터가 존재하지 않는 상태입니다: gamePk={}", gamePk);
            return;
        }
        CachedPlay cached = currentPlayCache.get(matchId);
        CurrentPlay cachedPlay = cached == null ? null : cached.play();

        //캐시가 없으면(재시작 직후) DB의 마지막 저장 타석 이후를 backfill, 타석이 바뀌었으면 직전 타석까지 저장
        if(cachedPlay == null || cachedPlay.atBatIndex() == null || cachedPlay.atBatIndex() < response.atBatIndex()){
            saveMatchService.saveAtBat(matchId, gamePk);
        }
        //이전데이터와 다른 데이터를 응답받았을 경우 캐시 갱신 후 웹소켓 전송
        if(!response.equals(cachedPlay)){
            log.info("실시간 타석데이터를 전송하였습니다: gamePk= {} ",gamePk);
            currentPlayCache.put(matchId, new CachedPlay(response, clock.instant()));
            messagingTemplate.convertAndSend(LIVE_DESTINATION_PREFIX + matchId, response);
            return;
        }

        //일정 시간 타석 갱신이 없으면 status 재확인 (종료 판정이 아니라 재확인 트리거)
        if(Duration.between(cached.changedAt(), clock.instant()).compareTo(properties.getCurrentPlay().getGameStatusCheckInterval()) < 0) return;

        Matches match = matchesRepository.findById(matchId).orElse(null);
        MlbGameState state = fetchGameState(gamePk);
        if(match == null || state == null) return;

        switch (state.status()){
            case FINAL -> finalizeMatch(match, state);
            //타석 폴링을 멈추고 status만 반복 조회
            case DELAYED -> onDelayed(match, true);
            case CANCELED -> cancelMatch(match);
            default -> {
                //LIVE 유지: 다음 주기에 다시 확인
            }
        }
    }

    //LIVE 진입. 첫 시작이면 수비/라인업 저장, 지연·재시작 후 복귀면 현재 타석까지 저장 후 타석 폴링 재개
    private void onLive(Matches match){
        boolean resumed = match.getRealStartedTime() != null;
        MatchStatusService.Transition transition = matchStatusService.changeStatus(match.getId(), MatchStatus.LIVE, now());
        if(transition.outcome() == MatchStatusService.Outcome.REJECTED || transition.outcome() == MatchStatusService.Outcome.NOT_FOUND) return;

        if(resumed) saveAtBatSafely(match);
        else openMatch(match.getId(), match.getGamePk());
        eventPublisher.publishEvent(new MatchStartedEvent(match.getId(), match.getGamePk()));
    }

    //경기가 시작되면 수비/라인업을 1회 저장. 실패는 재시도 예약으로 처리되어 중계 시작을 막지 않음
    private void openMatch(Long matchId, int gamePk){
        retryExecutor.execute("defense-location:" + matchId, () -> saveMatchService.saveDefenseLocationToRedis(matchId, gamePk));
        retryExecutor.execute("line-up:" + matchId, () -> saveMatchService.saveBattingOrderLineUp(matchId));
    }

    //DELAYED: 시작 전 지연은 12시간, 경기 중 지연은 6시간까지 status 반복 조회 후 상태 불명 처리
    private void onDelayed(Matches match, boolean fromLivePolling){
        MatchStatusService.Transition transition = matchStatusService.changeStatus(match.getId(), MatchStatus.DELAYED, now());
        if(transition.outcome() == MatchStatusService.Outcome.REJECTED || transition.outcome() == MatchStatusService.Outcome.NOT_FOUND) return;

        Matches updated = transition.match();
        Duration limit = updated.getRealStartedTime() == null
                ? properties.getPlayInfo().getDelayBeforeStartLimit()
                : properties.getPlayInfo().getDelayInGameLimit();

        if(now().isAfter(updated.getDelayedSince().plus(limit))){
            MatchStatusService.Transition suspended = matchStatusService.changeStatus(match.getId(), MatchStatus.MATCH_SUSPENDED_UNKNOWN, now());
            if(suspended.changed()){
                log.warn("지연 한도({}) 초과로 상태 불명 처리: matchId={}", limit, match.getId());
                eventPublisher.publishEvent(new MatchSuspendedUnknownEvent(match.getId()));
            }
            endPolling(match);
            return;
        }
        if(fromLivePolling){
            log.info("경기 중 지연으로 타석 폴링 중단, status 폴링 전환: matchId={}", match.getId());
            eventPublisher.publishEvent(new MatchStatusPollingEvent(match.getId(), match.getGamePk()));
        }
    }

    //FINAL 확정: 1) 마지막 타석까지 저장 2) 점수·승리팀·종료시각·상태를 한 트랜잭션으로 저장 3) 커밋 후 이벤트
    private void finalizeMatch(Matches match, MlbGameState state){
        if(!state.hasScore()){
            log.error("FINAL 응답에 점수가 없어 확정을 보류합니다(다음 폴링에서 재시도): matchId={}", match.getId());
            return;
        }
        //타석이 빠진 채 확정되면 이후 보완할 수 없으므로, 저장 실패 시 확정을 보류하고 다음 폴링에서 재시도
        if(!saveAtBatSafely(match)) return;

        MatchStatusService.Transition transition = matchStatusService.finalizeMatch(match.getId(), state.homeRuns(), state.awayRuns(), now());
        if(transition.changed()) eventPublisher.publishEvent(new MatchFinalizedEvent(match.getId()));
        if(transition.outcome() != MatchStatusService.Outcome.REJECTED) endPolling(match);
    }

    private void cancelMatch(Matches match){
        MatchStatusService.Transition transition = matchStatusService.changeStatus(match.getId(), MatchStatus.CANCELED, now());
        if(transition.changed()) eventPublisher.publishEvent(new MatchCanceledEvent(match.getId()));
        if(transition.outcome() != MatchStatusService.Outcome.REJECTED) endPolling(match);
    }

    private void stopIfStartPollingExpired(Matches match){
        LocalDateTime limit = match.getScheduledStartedTime().plus(properties.getPlayInfo().getMatchStartPollingLimit());
        if(now().isAfter(limit)){
            log.warn("시작 예정 시간으로부터 {} 이상 경기가 시작되지 않아 폴링을 종료합니다: matchId={}",
                    properties.getPlayInfo().getMatchStartPollingLimit(), match.getId());
            endPolling(match);
        }
    }

    private boolean saveAtBatSafely(Matches match){
        try {
            saveMatchService.saveAtBat(match.getId(), match.getGamePk());
            return true;
        } catch (Exception e) {
            log.error("타석 저장 실패: matchId={}", match.getId(), e);
            return false;
        }
    }

    private void endPolling(Matches match){
        currentPlayCache.remove(match.getId());
        eventPublisher.publishEvent(new MatchEndedPollingEvent(match.getId(), match.getGamePk()));
    }

    private MlbGameState fetchGameState(int gamePk){
        return MlbGameState.from(mlbApiClient.getGameStatus(gamePk));
    }

    private LocalDateTime now(){
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }

}
