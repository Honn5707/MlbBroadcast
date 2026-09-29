package com.mlbbroadcast.match.service;


import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.external.mlbstatus.MlbApiClient;
import com.mlbbroadcast.external.mlbstatus.dto.currentPlays.CurrentPlayResponse;
import com.mlbbroadcast.external.mlbstatus.dto.defenseLocation.DefenseLocationResponse;
import com.mlbbroadcast.external.mlbstatus.dto.gameStatus.GameData;
import com.mlbbroadcast.external.mlbstatus.dto.gameStatus.Status;
import com.mlbbroadcast.external.mlbstatus.dto.scheduled.ScheduledListResponse;
import com.mlbbroadcast.match.MatchScheduler;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.team.TeamMaster;
import com.mlbbroadcast.team.TeamMasterRepository;
import com.mlbbroadcast.util.RedisUtilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotalMatchService {
    private final MlbApiClient mlbApiClient;
    private final DefaultProperties configuration;
    private final RedisUtilities redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchesRepository matchesRepository;
    private final MatchScheduler matchScheduler;

    private final SaveMatchService saveMatchService;
    private final TeamMasterRepository teamMasterRepository;


    public void updateScheduleListForMatch(LocalDate date){
        for(int i = 0; i <14; i++) saveMatchService.saveScheduledMatches(date.plusDays(i));

    }
    //매치 스케쥴러가 경기 시작 시간이 되면 반복 폴링하는 메서드. 라인업을 최신화. 해당 메서드를 통해 경기가 시작되었다면 폴링 주체를 liveData에게 넘김.
    public void openMatch(Long matchId, int gamePk){
        saveDefenseLocationToRedis(matchId, gamePk);
        saveMatchService.saveBattingOrderLineUp(matchId);
        matchScheduler.startPollingForUpdateMatch(matchId, gamePk);

    }



    @Transactional
    public void checkMatchStart(Long matchId){
        Matches matches = matchesRepository.findById(matchId).orElseThrow(()->new BusinessException(ErrorCode.MATCH_NOT_FOUND));
        int gamePk = matches.getExternalId();
        GameData response = mlbApiClient.getGameStatus(gamePk).gameData();

        if(response == null) throw new BusinessException(ErrorCode.MLB_STATS_NOT_RESPONSE);
        //Live일 경우 경기 중계 시작  ->
        switch (response.status().abstractGameState()){

            case null-> log.error("abstractGameState필드가 정상적으로 받아지지 않았습니다.");

            case "Live" -> {
                //매치가 시작되었을 경우 타이머 해제 및 매치 시작 로직 시작
                matches.matchStatusChange(MatchStatus.PLAYED);
                matches.matchStartTimeSet(Instant.parse(response.datetime().dateTime()).atZone(ZoneId.of("UTC")).toLocalDateTime());
                matchScheduler.endedPolling(matchId);
                openMatch(matchId, gamePk);

            }
            case "Final" -> {
                matches.matchStatusChange(MatchStatus.FINISHED);
                matchScheduler.endedPolling(matchId);
            }
            default -> {
                //해당 안될 경우 무한 폴링

            }





        }

    }


    public void saveDefenseLocationToRedis(Long matchId,int gamePk){
        //수비는 저장할 필요없는 가변적인 값. 매 타석 변경시 redis캐시 메모리에 갱신
        DefenseLocationResponse response = mlbApiClient.getDefenseLineUp(gamePk);
        redis.save("DefenseLineUp:"+matchId, response);


    }
    //  실시간 타석 정보 데이터를 외부 API로부터 받아오는 메서드 (external.mlbstatus 디렉토리 참고)
    // 해당 로직은 스케쥴 경기시간동안 스케쥴러가 10초마다 폴링. ->타석 종료 시, 타석데이터를 덮어씀
    public void fetchCurrentPlayData(Long matchId, int gamePk){
        String key =  configuration.getCurrentPlay().getCurrentDataKeyIndex() + matchId;
        CurrentPlayResponse response = mlbApiClient.getCurrentPlay(gamePk);
        CurrentPlayResponse cachedCurrentPlayValue = redis.load("currentPlay:"+key, CurrentPlayResponse.class);
        //현재 받아온 키가 기존키에서 갱신된 형태라면  타석 업데이트 및 DB세이브
        if(cachedCurrentPlayValue ==null || cachedCurrentPlayValue.atBatIndex() < response.atBatIndex()) saveMatchService.saveAtBat(matchId, gamePk,response.atBatIndex());
        //이전데이터와 다른 데이터를 응답받았을 경우 받아온 데이터를 redis캐시 메모리에 저장후 웹소켓을 통한 전송
        if(!response.equals(cachedCurrentPlayValue)){
            redis.save("currentPlay:"+key, response);
            //json데이터를  클라이언트에게 전송
            messagingTemplate.convertAndSend("/topic/games/" + matchId + "/current-plays", response);

        }
    }




    //이닝 업데이트 (
    public void updateInning(Long matchId, int gamePk){
        saveDefenseLocationToRedis(matchId, gamePk);

    }
}
