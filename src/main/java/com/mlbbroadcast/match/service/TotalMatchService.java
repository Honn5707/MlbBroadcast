package com.mlbbroadcast.match.service;


import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlay;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.GameData;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotalMatchService {
    private final MlbApiClient mlbApiClient;
    private final DefaultProperties configuration;
    private final RedisUtilities redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchesRepository matchesRepository;
    private final ApplicationEventPublisher eventPublisher;


    private final SaveMatchService saveMatchService;


    public void updateScheduleListForMatch(LocalDate date){
        for(int i = 0; i <14; i++) saveMatchService.saveScheduledMatches(date.plusDays(i));

    }
    //매치 스케쥴러가 경기 시작 시간이 되면 반복 폴링하는 메서드. 라인업을 최신화. 해당 메서드를 통해 경기가 시작되었다면 폴링 주체를 liveData에게 넘김.
    public void openMatch(Long matchId, int gamePk){
        saveMatchService.saveDefenseLocationToRedis(matchId, gamePk);
        saveMatchService.saveBattingOrderLineUp(matchId);
        eventPublisher.publishEvent(new MatchStartedEvent(matchId, gamePk));



    }



    @Transactional
    public void checkMatchStart(Long matchId){
        Matches matches = matchesRepository.findById(matchId).orElseThrow(()->new BusinessException(ErrorCode.MATCH_NOT_FOUND));
        int gamePk = matches.getGamePk();
        GameData response = mlbApiClient.getGameStatus(gamePk).gameData();

        if(response == null) throw new BusinessException(ErrorCode.MLB_STATS_NOT_RESPONSE);
        //Live일 경우 경기 중계 시작  ->
        switch (response.status().abstractGameState()){

            case null-> log.error("abstractGameState필드가 정상적으로 받아지지 않았습니다.");

            case "Live" -> {
                //매치가 시작되었을 경우 타이머 해제 및 매치 시작 로직 시작
                matches.matchStatusChange(MatchStatus.PLAYED);
                matches.matchStartTimeSet(Instant.parse(response.datetime().dateTime()).atZone(ZoneId.of("UTC")).toLocalDateTime());
                openMatch(matchId, gamePk);

            }
            case "Final" -> {
                matches.matchStatusChange(MatchStatus.FINISHED);
                eventPublisher.publishEvent(new MatchEndedPollingEvent(matchId));
            }
            default -> {
                //해당 안될 경우 무한 폴링

            }





        }

    }


    //  실시간 타석 정보 데이터를 외부 API로부터 받아오는 메서드 (external.mlbstatus 디렉토리 참고)
    // 해당 로직은 스케쥴 경기시간동안 스케쥴러가 10초마다 폴링. ->타석 종료 시, 타석데이터를 덮어씀
    public void fetchCurrentPlayData(Long matchId, int gamePk){
        String key =  configuration.getCurrentPlay().getCurrentDataKeyIndex() + matchId;
        CurrentPlay response = mlbApiClient.getCurrentPlay(gamePk).currentPlay();
        log.info(""+response);
        if(response.atBatIndex() == null){
            log.info("타석데이터가 존재하지 않는 상태입니다: "+gamePk);
            return;
        }
        CurrentPlay cachedCurrentPlayValue = redis.load("currentPlay:"+key, CurrentPlay.class);
        //현재 받아온 키가 기존키에서 갱신된 형태라면  타석 업데이트 및 DB세이브
        if(cachedCurrentPlayValue ==null || cachedCurrentPlayValue.atBatIndex() < response.atBatIndex()){
            saveMatchService.saveAtBat(matchId, gamePk);
        }
        //이전데이터와 다른 데이터를 응답받았을 경우 받아온 데이터를 redis캐시 메모리에 저장후 웹소켓을 통한 전송
        if(!response.equals(cachedCurrentPlayValue)){
            redis.save("currentPlay:"+key, response);
            log.info("현재 redis 키 저장 : " + key);
            //json데이터를  클라이언트에게 전송
            messagingTemplate.convertAndSend("/topic/games/" + matchId + "/current-plays", response);

        }
    }





}
