package com.mlbbroadcast.match;


import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.external.mlbstatus.MlbApiClient;
import com.mlbbroadcast.external.mlbstatus.dto.allPlays.AllPlaysResponse;
import com.mlbbroadcast.external.mlbstatus.dto.lineUp.LineUpResponse;
import com.mlbbroadcast.match.entities.LineUp;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.entities.MatchplayLog;
import com.mlbbroadcast.match.entities.PlayEvent;
import com.mlbbroadcast.match.repositories.LineUpRepository;
import com.mlbbroadcast.match.repositories.MatchPlaylogRepository;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.match.repositories.PlayEventRepository;
import com.mlbbroadcast.player.entity.PlayerMaster;
import com.mlbbroadcast.player.repository.PlayerMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Service
@RequiredArgsConstructor
public class SaveMatchService {
    private final MlbApiClient mlbApiClient;
    private final PlayerMasterRepository playerMasterRepository;
    private final MatchPlaylogRepository matchPlaylogRepository;
    private final PlayEventRepository playEventRepository;
    private final MatchesRepository matchesRepository;
    private final LineUpRepository lineUpRepository;

    //전송 되어야 할 데이터: 선수 라인업 , MatchData(DataBase)
    public void saveLineUp(Long matchId){
        Matches match = matchesRepository.findById(matchId).orElseThrow(()->new BusinessException(ErrorCode.MATCH_NOT_FOUND));
        LineUpResponse response =  mlbApiClient.getLineUp(match.getExternal_id());
        List<LineUp> battingOrderList = battingOrderListMaker(response.teams().home().battingOrder(), matchId);
        battingOrderList.addAll(battingOrderListMaker(response.teams().away().battingOrder(), matchId));
        lineUpRepository.saveAll(battingOrderList);
    }
    private List<LineUp> battingOrderListMaker(List<Integer> battingOrderExternalIdList, Long matchId){
        List<LineUp> lineUpList = new ArrayList<>();
        for(int idx = 0; idx < battingOrderExternalIdList.size(); idx++){
            int externalId = battingOrderExternalIdList.get(idx);
            PlayerMaster player = playerMasterRepository.findByExternalId(externalId).orElseThrow(()->new BusinessException(ErrorCode.PLAYER_NOT_FOUND));
            lineUpList.add(LineUp.builder().matchId(matchId).playerId(player.getId()).battingOrder(idx+1).externalId(externalId).teamId(player.getTeamId()).build());
        }
        return lineUpList;
    }



    //   타석 종료 DB데이터 갱신하는 메서드
    @Transactional
    public void updateAtBat(Long matchId, Long gamePk, int atBatIndex){
        AllPlaysResponse response = mlbApiClient.getAllPlays(gamePk);
        List<MatchplayLog> matchplayLogList = response.allPlays().stream().filter(play -> play.atBatIndex() > atBatIndex) .map(allPlays -> {

            PlayerMaster batter = playerMasterRepository.findByExternalId(allPlays.matchup().batter().id()).orElse(null);

            PlayerMaster pitcher = playerMasterRepository.findByExternalId(allPlays.matchup().pitcher().id()).orElse(null);
            return  MatchplayLog.builder().matchId(matchId).atBatIndex(allPlays.atBatIndex()).batterId(batter==null?null: batter.getId()).batterExternalId(allPlays.matchup().batter().id()).pitcherExternalId(allPlays.matchup().pitcher().id()).
                    pitcherId(pitcher==null? null: pitcher.getId())
                    .resultDescription(allPlays.result().description())
                    .inning(allPlays.about().inning())
                    .build();


        }).toList();
        if (matchplayLogList.isEmpty()) {
            log.warn("updateAtBat 호출됐지만 새로 저장할 타석이 없음: matchId={}, atBatIndex={}", matchId, atBatIndex);
            return;
        }

        //saveAll -> matchPlayLogId리턴
        List<MatchplayLog> savedLogs = matchPlaylogRepository.saveAll(matchplayLogList);
        //인덱스로 탐색 시간을 줄이기
        Map<Integer, MatchplayLog> saveLogsMap = savedLogs.stream()
                .collect(Collectors.toMap(MatchplayLog::getAtBatIndex, log -> log));

        List<PlayEvent> totalPlayEventList =  response.allPlays().stream().filter(play -> play.atBatIndex() > atBatIndex).flatMap(allPlays -> {

            MatchplayLog matchplayLog = saveLogsMap.get(allPlays.atBatIndex());
            if(matchplayLog == null) throw new BusinessException(ErrorCode.MATCH_LOG_NOT_FOUND);
            Long realId = matchplayLog.getId();
            List<PlayEvent> abBatPlayEventList = allPlays.playEvents().stream().map(playEventsItem -> {

                PlayEvent playEvent;

                if (playEventsItem.type().equals("pitch"))
                    playEvent = PlayEvent.builder().matchPlayLogId(realId)
                            .event(playEventsItem.details().event())
                            .description(playEventsItem.details().description())
                            .startSpeed(playEventsItem.pitchData().startSpeed())
                            .endSpeed(playEventsItem.pitchData().endSpeed())
                            .pitchTypeDescription(playEventsItem.details().type().description())
                            .build();
                else playEvent = PlayEvent.builder()
                        .matchPlayLogId(realId)
                        .event(playEventsItem.details().event())
                        .description(playEventsItem.details().description())
                        .build();
                return playEvent;
            }).toList();


            return abBatPlayEventList.stream();
        }).toList();
        playEventRepository.saveAll(totalPlayEventList);


    }
}
