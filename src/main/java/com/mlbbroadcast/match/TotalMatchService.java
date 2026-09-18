package com.mlbbroadcast.match;


import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.external.mlbstatus.MlbApiClient;
import com.mlbbroadcast.external.mlbstatus.dto.currentPlays.CurrentPlayResponse;
import com.mlbbroadcast.external.mlbstatus.dto.gameStatus.Status;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.util.RedisUtilities;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import static reactor.netty.http.HttpConnectionLiveness.log;

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

    //매치 스케쥴러가 경기 시작 시간  폴링하는 메서드. 라인업을 최신화. 해당 메서드를 통해 경기가 시작되었다면 폴링 주체를 liveData에게 넘김.
    public void openMatch(Long matchId){
        Long matchExternalId = matchesRepository.findById(matchId).orElseThrow(()->new BusinessException(ErrorCode.MATCH_NOT_FOUND)).getExternal_id();
        Status response = mlbApiClient.getGameStatus(matchExternalId).status();
        if(response == null) throw new BusinessException(ErrorCode.MLB_STATS_NOT_RESPONSE);
        //Live일 경우 경기 중계 시작  ->
        switch (response.abstractGameState()){

            case null-> log.error("abstractGameState필드가 정상적으로 받아지지 않았습니다.");

            case "Live" -> {

            }
            default -> {

            }





        }





    }

    //  실시간 타석 정보 데이터를 외부 API로부터 받아오는 메서드 (external.mlbstatus 디렉토리 참고)
    // 해당 로직은 스케쥴 경기시간동안 스케쥴러가 10초마다 폴링. ->타석 종료 시, 타석데이터를 덮어씀
    public void fetchCurrentPlayData(Long gamePk, Long matchId){
        String key =  configuration.getCurrentPlay().getCurrentDataKeyIndex() + matchId;
        CurrentPlayResponse response = mlbApiClient.getCurrentPlay(gamePk);
        CurrentPlayResponse cachedCurrentPlayValue = redis.load(key, CurrentPlayResponse.class);
        //현재 받아온 키가 기존키에서 갱신된 형태라면  타석 업데이트 및 DB세이브
        if(cachedCurrentPlayValue ==null || cachedCurrentPlayValue.atBatIndex() < response.atBatIndex()) saveMatchService.updateAtBat(matchId, gamePk,response.atBatIndex());
        //이전데이터와 다른 데이터를 응답받았을 경우 받아온 데이터를 redis캐시 메모리에 저장후 웹소켓을 통한 전송
        if(!response.equals(cachedCurrentPlayValue)){
            redis.save(key, response);
            //json데이터를  클라이언트에게 전송
            messagingTemplate.convertAndSend("/topic/games/" + matchId + "/current-plays", response);

        }
    }




    //이닝이 시작되었을때 (매 타석이 끝날때 캐시메모리에 저장된 이닝과 을 통한 비교)
    public void updateInning(Long matchId, Long gamePk){
        matchesRepository.findById(gamePk);

    }
}
