package com.mlbbroadcast.match.service;


import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlay;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlayResponse;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.GameStatusResponse;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.Status;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.event.MatchEndedPollingEvent;
import com.mlbbroadcast.match.event.MatchStartedEvent;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.util.RedisUtilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotalMatchService {
    private static final String CURRENT_PLAY_KEY_PREFIX = "currentPlay:";
    private static final String GAME_STATE_LIVE = "Live";
    private static final String GAME_STATE_FINAL = "Final";
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final MlbApiClient mlbApiClient;
    private final DefaultProperties properties;
    private final RedisUtilities redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchesRepository matchesRepository;
    private final ApplicationEventPublisher eventPublisher;


    private final SaveMatchService saveMatchService;


    public void updateScheduleListForMatch(LocalDate date){
        for(int i = 0; i < 14; i++) {
            LocalDate targetDate = date.plusDays(i);
            //하루 저장이 실패해도 나머지 날짜는 저장되도록 날짜별로 예외 처리
            try {
                saveMatchService.saveScheduledMatches(targetDate);
            } catch (Exception e) {
                log.error("경기 일정 저장 실패: date={}", targetDate, e);
            }
        }
    }
    //경기가 시작되면 수비/라인업을 저장하고 폴링 주체를 liveData에게 넘김. 저장 실패가 중계 시작을 막지 않도록 개별 처리
    public void openMatch(Long matchId, int gamePk){
        try {
            saveMatchService.saveDefenseLocationToRedis(matchId, gamePk);
        } catch (Exception e) {
            log.error("수비 위치 저장 실패: matchId={}", matchId, e);
        }
        try {
            saveMatchService.saveBattingOrderLineUp(matchId);
        } catch (Exception e) {
            log.error("라인업 저장 실패: matchId={}", matchId, e);
        }
        eventPublisher.publishEvent(new MatchStartedEvent(matchId, gamePk));
    }


    //매치 스케쥴러가 경기 시작 시간이 되면 반복 폴링하는 메서드
    //외부 API 호출이 포함되어 있어 트랜잭션을 걸지 않고, 상태 변경은 save로 명시적으로 반영
    public void checkMatchStart(Long matchId){
        Matches matches = matchesRepository.findById(matchId).orElse(null);
        if(matches == null){
            log.error("경기를 찾을 수 없어 시작 감지 폴링을 종료합니다: matchId={}", matchId);
            eventPublisher.publishEvent(new MatchEndedPollingEvent(matchId));
            return;
        }
        //연기/지연 등으로 경기가 시작되지 않으면 무한 폴링되지 않도록 제한
        if(isStartPollingExpired(matches)){
            log.warn("시작 예정 시간으로부터 {} 이상 경기가 시작되지 않아 폴링을 종료합니다: matchId={}",
                    properties.getPlayInfo().getMatchStartPollingLimit(), matchId);
            eventPublisher.publishEvent(new MatchEndedPollingEvent(matchId));
            return;
        }

        int gamePk = matches.getGamePk();
        Status status = fetchGameStatus(gamePk);
        if(status == null || status.abstractGameState() == null){
            log.error("abstractGameState필드가 정상적으로 받아지지 않았습니다: gamePk={}", gamePk);
            return;
        }

        switch (status.abstractGameState()){
            case GAME_STATE_LIVE -> {
                //매치가 시작되었을 경우 타이머 해제 및 매치 시작 로직 시작
                matches.matchStatusChange(MatchStatus.PLAYED);
                matches.matchStartTimeSet(LocalDateTime.now(UTC));
                matchesRepository.save(matches);
                openMatch(matchId, gamePk);
            }
            case GAME_STATE_FINAL -> finishMatch(matches);
            default -> {
                //경기 전 상태. 다음 폴링에서 다시 확인
            }
        }
    }


    //  실시간 타석 정보 데이터를 외부 API로부터 받아오는 메서드 (external.mlbstatus 디렉토리 참고)
    // 해당 로직은 스케쥴 경기시간동안 스케쥴러가 10초마다 폴링. ->타석 종료 시, 타석데이터를 덮어씀
    public void fetchCurrentPlayData(Long matchId, int gamePk){
        String key = CURRENT_PLAY_KEY_PREFIX + properties.getCurrentPlay().getCurrentDataKeyIndex() + matchId;
        CurrentPlayResponse currentPlayResponse = mlbApiClient.getCurrentPlay(gamePk);
        CurrentPlay response = currentPlayResponse == null ? null : currentPlayResponse.currentPlay();

        log.info("현재 타석 데이터 처리: gamePk={}", gamePk);
        if(response == null || response.atBatIndex() == null){
            log.info("타석데이터가 존재하지 않는 상태입니다: gamePk={}", gamePk);
            return;
        }
        CurrentPlay cachedCurrentPlayValue = redis.load(key, CurrentPlay.class);
        //현재 받아온 키가 기존키에서 갱신된 형태라면  타석 업데이트 및 DB세이브
        if(cachedCurrentPlayValue == null || cachedCurrentPlayValue.atBatIndex() == null
                || cachedCurrentPlayValue.atBatIndex() < response.atBatIndex()){
            saveMatchService.saveAtBat(matchId, gamePk);
        }
        //이전데이터와 다른 데이터를 응답받았을 경우 받아온 데이터를 redis캐시 메모리에 저장후 웹소켓을 통한 전송
        if(!response.equals(cachedCurrentPlayValue)){
            redis.save(key, response, properties.getCurrentPlay().getCurrentPlayCacheDuration());
            log.info("현재 redis 키 저장 : {}", key);
            //json데이터를  클라이언트에게 전송
            messagingTemplate.convertAndSend("/topic/games/" + matchId + "/current-plays", response);
            return;
        }

        //일정 시간 이상 타석 데이터가 최신화되지 않았다면 외부 api를 받아와 게임 종료 검증
        if(isCurrentPlayStale(key)){
            Status status = fetchGameStatus(gamePk);
            if(status != null && GAME_STATE_FINAL.equals(status.abstractGameState()))
                endMatch(matchId, gamePk);
        }
    }

    //경기종료 -> api 스케쥴링 종료
    public void endMatch(Long matchId, int gamePk){
        Matches match = matchesRepository.findById(matchId).orElse(null);
        if(match == null){
            log.error("경기를 찾을 수 없어 폴링을 종료합니다: matchId={}, gamePk={}", matchId, gamePk);
            eventPublisher.publishEvent(new MatchEndedPollingEvent(matchId));
            return;
        }
        finishMatch(match);
    }

    private void finishMatch(Matches match){
        log.info("경기종료: gamePk={}", match.getGamePk());
        //마지막 타석 저장이 실패해도 폴링은 종료되도록 예외 처리
        try {
            saveMatchService.saveAtBat(match.getId(), match.getGamePk());
        } catch (Exception e) {
            log.error("경기 종료 시 타석 저장 실패: matchId={}", match.getId(), e);
        }
        match.matchStatusChange(MatchStatus.FINISHED);
        match.matchEndTimeSet(LocalDateTime.now(UTC));
        //스케쥴러 스레드에는 트랜잭션이 없어 변경 감지가 동작하지 않으므로 명시적으로 저장
        matchesRepository.save(match);
        eventPublisher.publishEvent(new MatchEndedPollingEvent(match.getId()));
    }

    private Status fetchGameStatus(int gamePk){
        GameStatusResponse response = mlbApiClient.getGameStatus(gamePk);
        if(response == null || response.gameData() == null) return null;
        return response.gameData().status();
    }

    private boolean isStartPollingExpired(Matches matches){
        LocalDateTime limit = matches.getScheduledStartedTime().plus(properties.getPlayInfo().getMatchStartPollingLimit());
        return LocalDateTime.now(UTC).isAfter(limit);
    }

    //캐시는 갱신될 때마다 TTL이 초기화되므로, 남은 TTL로 마지막 갱신 이후 경과 시간을 계산
    private boolean isCurrentPlayStale(String key){
        Long remainSeconds = redis.expireDuration(key);
        if(remainSeconds == null || remainSeconds < 0) return true;
        Duration elapsed = properties.getCurrentPlay().getCurrentPlayCacheDuration().minusSeconds(remainSeconds);
        return elapsed.compareTo(properties.getCurrentPlay().getGameStatusCheckInterval()) >= 0;
    }

}
