package com.mlbbroadcast.match.service;


import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.allPlays.About;
import com.mlbbroadcast.external.mlbStatus.dto.allPlays.AllPlaysResponse;
import com.mlbbroadcast.external.mlbStatus.dto.batterOrderLineUp.LineUpResponse;
import com.mlbbroadcast.external.mlbStatus.dto.defenseLocation.DefenseLocationResponse;
import com.mlbbroadcast.external.mlbStatus.dto.scheduled.GamesItem;
import com.mlbbroadcast.external.mlbStatus.dto.scheduled.ScheduledListResponse;
import com.mlbbroadcast.match.entities.LineUp;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.entities.MatchplayLog;
import com.mlbbroadcast.match.entities.PlayEvent;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.event.MatchScheduledSaveEvent;
import com.mlbbroadcast.match.repositories.LineUpRepository;
import com.mlbbroadcast.match.repositories.MatchPlaylogRepository;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.match.repositories.PlayEventRepository;
import com.mlbbroadcast.player.entity.PlayerMaster;
import com.mlbbroadcast.player.repository.PlayerMasterRepository;
import com.mlbbroadcast.team.TeamMaster;
import com.mlbbroadcast.team.TeamMasterRepository;
import com.mlbbroadcast.util.RedisUtilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class SaveMatchService {
    private final MlbApiClient mlbApiClient;
    private final PlayerMasterRepository playerMasterRepository;
    private final MatchPlaylogRepository matchPlaylogRepository;
    private final PlayEventRepository playEventRepository;
    private final MatchesRepository matchesRepository;
    private final LineUpRepository lineUpRepository;
    private final TeamMasterRepository teamMasterRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisUtilities redis;


    //scheduled 데이터를 호출 ( 파라메터로부터 +5days )

    @Transactional
    public void saveScheduledMatches(LocalDate date){

        ScheduledListResponse response = mlbApiClient.getListOfSchedule(date);
        if(response.dates().isEmpty()){
            log.info("로그:"+date+": 경기일정 없음");
            return;
        }

        List<GamesItem> gamesItemList = response.dates().getFirst().games();
        Map<Integer, TeamMaster> activeTeams = teamMasterRepository.findAllByIsActive(true).stream().collect(Collectors.toMap(TeamMaster::getExternalId, team->team));
        Set<Integer> matchExternalIds = matchesRepository.findExistingGamePk(gamesItemList.stream().map(GamesItem::gamePk).toList());
        List<Matches> matchesList = gamesItemList.stream()
                .filter(gamesItem ->!matchExternalIds.contains(gamesItem.gamePk()))
                .filter(gamesItem -> activeTeams.containsKey(gamesItem.teams().home().team().id()) && activeTeams.containsKey(gamesItem.teams().away().team().id()))
                .map(gamesItem ->
            {
            Long homeTeamId = activeTeams.get(gamesItem.teams().home().team().id()).getId();
            Long visitTeamId = activeTeams.get(gamesItem.teams().away().team().id()).getId();



            //매 시즌이 시작될떄 팀 마스터테이블을 등록 << 시즌중 팀 해체 X. 즉, 팀id가 존재하지않을경우 예외발생
//            if(homeTeamId == null || visitTeamId == null) throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
            return Matches.builder().homeTeamId(homeTeamId).visitTeamId(visitTeamId).matchStatus(MatchStatus.BEFORE).gamePk(gamesItem.gamePk()).seasonYear(gamesItem.season()).scheduledStartedTime(Instant.parse(gamesItem.gameDate()).atZone(ZoneId.of("UTC")).toLocalDateTime()).build();


        }).toList();
        //matchList가 비어있지않을때(스케쥴이 존재할때)
        if(!matchesList.isEmpty()) {

            List<Matches> savedMatches = matchesRepository.saveAll(matchesList);
            savedMatches.forEach(matches ->  eventPublisher.publishEvent(new MatchScheduledSaveEvent(matches)));
        }
    }
    //전송 되어야 할 데이터: 선수 라인업 , MatchData(DataBase)
    @Transactional
    public void saveBattingOrderLineUp(Long matchId){
//        Matches match = matchesRepository.findById(matchId).orElseThrow(()->new BusinessException(ErrorCode.MATCH_NOT_FOUND));
//        LineUpResponse response =  mlbApiClient.getLineUp(match.getGamePk());
//        List<LineUp> battingOrderList = battingOrderListMaker(response.teams().home().battingOrder(), matchId);
//        battingOrderList.addAll(battingOrderListMaker(response.teams().away().battingOrder(), matchId));
//        lineUpRepository.saveAll(battingOrderList);
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
    public void saveAtBat(Long matchId, int gamePk){
        AllPlaysResponse response = mlbApiClient.getAllPlays(gamePk);

        if(response.allPlays().isEmpty()){
            log.info("현재 갱신된 타석 데이터가 존재하지 않습니다.");
            return;
        }
        About lastInningAbout = response.allPlays().getLast().about();

        MatchplayLog lastMatchLog = matchPlaylogRepository.findByLatestMatchPlayLog(matchId).orElse(null);
        int lastMatchAtBatIndex = lastMatchLog == null ? -1 : lastMatchLog.getAtBatIndex();
        //response, playEvent의 마지막 요소의 이닝필드가 변화되었을떄



        List<MatchplayLog> matchplayLogList = response.allPlays().stream().filter(play -> play.atBatIndex() > lastMatchAtBatIndex) .map(allPlays -> {

            PlayerMaster batter = playerMasterRepository.findByExternalId(allPlays.matchup().batter().id()).orElse(null);

            PlayerMaster pitcher = playerMasterRepository.findByExternalId(allPlays.matchup().pitcher().id()).orElse(null);
            return  MatchplayLog.builder().matchId(matchId).atBatIndex(allPlays.atBatIndex()).batterId(batter==null?null: batter.getId()).batterExternalId(allPlays.matchup().batter().id()).pitcherExternalId(allPlays.matchup().pitcher().id()).
                    pitcherId(pitcher==null? null: pitcher.getId())
                    .resultDescription(allPlays.result().description())
                    .inning(allPlays.about().inning())
                    .isTopInning(allPlays.about().isTopInning())
                    .build();


        }).toList();
        if (matchplayLogList.isEmpty()) {
            log.warn("updateAtBat 호출됐지만 새로 저장할 타석이 없음: matchId={}, atBatIndex={}", matchId, lastMatchAtBatIndex);
            return;
        }

        log.info("playLog저장완료");
        //saveAll -> matchPlayLogId리턴
        List<MatchplayLog> savedLogs = matchPlaylogRepository.saveAll(matchplayLogList);
        //인덱스로 탐색 시간을 줄이기
        Map<Integer, MatchplayLog> saveLogsMap = savedLogs.stream()
                .collect(Collectors.toMap(MatchplayLog::getAtBatIndex, log -> log));

        List<PlayEvent> totalPlayEventList =  response.allPlays().stream().filter(play -> play.atBatIndex() > lastMatchAtBatIndex).flatMap(allPlays -> {

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

    public void saveDefenseLocationToRedis(Long matchId,int gamePk){
        //수비는 저장할 필요없는 가변적인 값. 매 타석 변경시 redis캐시 메모리에 갱신
        DefenseLocationResponse response = mlbApiClient.getDefenseLineUp(gamePk);
        redis.save("DefenseLineUp:"+matchId, response);


    }

    //이닝 업데이트 (
    private void updateInning(Long matchId, int gamePk){
        saveDefenseLocationToRedis(matchId, gamePk);

    }

}
