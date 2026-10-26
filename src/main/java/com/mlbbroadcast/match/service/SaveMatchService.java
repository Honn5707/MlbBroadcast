package com.mlbbroadcast.match.service;


import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.allPlays.AllPlays;
import com.mlbbroadcast.external.mlbStatus.dto.allPlays.Details;
import com.mlbbroadcast.external.mlbStatus.dto.allPlays.PitchData;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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
    private final DefaultProperties properties;
    private final RedisUtilities redis;
    private final TransactionTemplate transactionTemplate;


    //해당 날짜의 일정을 조회해 없는 경기는 저장, 상태 불명(MATCH_SUSPENDED_UNKNOWN) 경기는 시작 시각 갱신 후 폴링 재등록
    //외부 API는 트랜잭션 밖에서 호출하고, 응답을 받은 뒤 DB 작업만 트랜잭션으로 묶음 (API 대기 중 DB 커넥션 점유 방지)
    public void saveScheduledMatches(LocalDate date){

        ScheduledListResponse response = mlbApiClient.getListOfSchedule(date);
        if(response == null || response.dates() == null || response.dates().isEmpty()){
            log.info("로그:{}: 경기일정 없음", date);
            return;
        }

        List<GamesItem> gamesItemList = response.dates().getFirst().games();
        transactionTemplate.executeWithoutResult(status -> persistScheduledMatches(gamesItemList));
    }

    private void persistScheduledMatches(List<GamesItem> gamesItemList){
        Map<Integer, TeamMaster> activeTeams = teamMasterRepository.findAllByIsActive(true).stream().collect(Collectors.toMap(TeamMaster::getExternalId, team->team));
        Map<Integer, Matches> existingMatches = matchesRepository.findAllByGamePkIn(gamesItemList.stream().map(GamesItem::gamePk).toList())
                .stream().collect(Collectors.toMap(Matches::getGamePk, match -> match));

        //다음날 일정 동기화에서 같은 gamePk를 만나면 상태를 다시 확인하도록 폴링 재등록
        //수정 대상만 락을 걸어 다시 조회 (락 순서를 id 오름차순으로 고정해 교착 방지), 락 획득 후 상태를 다시 확인
        gamesItemList.stream()
                .filter(gamesItem -> existingMatches.containsKey(gamesItem.gamePk()))
                .filter(gamesItem -> existingMatches.get(gamesItem.gamePk()).getMatchStatus() == MatchStatus.MATCH_SUSPENDED_UNKNOWN)
                .sorted(Comparator.comparing(gamesItem -> existingMatches.get(gamesItem.gamePk()).getId()))
                .forEach(gamesItem -> {
                    Matches match = matchesRepository.findByIdForUpdate(existingMatches.get(gamesItem.gamePk()).getId()).orElse(null);
                    if(match == null || match.getMatchStatus() != MatchStatus.MATCH_SUSPENDED_UNKNOWN) return;
                    match.reschedule(toUtc(gamesItem.gameDate()));
                    log.info("상태 불명 경기 재등록: matchId={}, gamePk={}", match.getId(), match.getGamePk());
                    eventPublisher.publishEvent(new MatchScheduledSaveEvent(match));
                });

        List<Matches> matchesList = gamesItemList.stream()
                .filter(gamesItem ->!existingMatches.containsKey(gamesItem.gamePk()))
                .filter(gamesItem -> activeTeams.containsKey(gamesItem.teams().home().team().id()) && activeTeams.containsKey(gamesItem.teams().away().team().id()))
                .map(gamesItem ->
            {
            Long homeTeamId = activeTeams.get(gamesItem.teams().home().team().id()).getId();
            Long visitTeamId = activeTeams.get(gamesItem.teams().away().team().id()).getId();



            //매 시즌이 시작될떄 팀 마스터테이블을 등록 << 시즌중 팀 해체 X. 즉, 팀id가 존재하지않을경우 예외발생
//            if(homeTeamId == null || visitTeamId == null) throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
            return Matches.builder().homeTeamId(homeTeamId).visitTeamId(visitTeamId).matchStatus(MatchStatus.SCHEDULED).gamePk(gamesItem.gamePk()).seasonYear(gamesItem.season()).scheduledStartedTime(toUtc(gamesItem.gameDate())).build();


        }).toList();
        //matchList가 비어있지않을때(스케쥴이 존재할때)
        if(!matchesList.isEmpty()) {

            List<Matches> savedMatches = matchesRepository.saveAll(matchesList);
            savedMatches.forEach(matches ->  eventPublisher.publishEvent(new MatchScheduledSaveEvent(matches)));
        }
    }
    //전송 되어야 할 데이터: 선수 라인업 , MatchData(DataBase)
    //경기 조회(gamePk, 팀 id 확인) -> 외부 API(트랜잭션 밖) -> 선수 매핑 + 저장(트랜잭션)
    public void saveBattingOrderLineUp(Long matchId){
        Matches match = matchesRepository.findById(matchId).orElseThrow(()->new BusinessException(ErrorCode.MATCH_NOT_FOUND));
        LineUpResponse response =  mlbApiClient.getLineUp(match.getGamePk());
        transactionTemplate.executeWithoutResult(status -> {
            List<LineUp> battingOrderList = battingOrderListMaker(response.teams().home().battingOrder(), matchId, match.getHomeTeamId());
            battingOrderList.addAll(battingOrderListMaker(response.teams().away().battingOrder(), matchId, match.getVisitTeamId()));
            lineUpRepository.saveAll(battingOrderList);
        });
    }
    //마스터에 없는 선수는 player_id를 null로 두고 external_id를 보존 (선수 동기화 후 재매핑 대상)
    //팀은 선수의 현재 소속이 아니라 경기의 홈/원정 팀 기준
    private List<LineUp> battingOrderListMaker(List<Integer> battingOrderExternalIdList, Long matchId, Long teamId){
        List<LineUp> lineUpList = new ArrayList<>();
        if(battingOrderExternalIdList == null) return lineUpList;
        for(int idx = 0; idx < battingOrderExternalIdList.size(); idx++){
            int externalId = battingOrderExternalIdList.get(idx);
            Long playerId = playerMasterRepository.findByExternalId(externalId).map(PlayerMaster::getId).orElse(null);
            if(playerId == null) log.warn("마스터에 없는 선수(라인업): matchId={}, externalId={}", matchId, externalId);
            lineUpList.add(LineUp.builder().matchId(matchId).playerId(playerId).battingOrder(idx+1).externalId(externalId).teamId(teamId).build());
        }
        return lineUpList;
    }

    //   타석 종료 DB데이터 갱신하는 메서드
    //외부 API(트랜잭션 밖) -> 저장된 타석 확인 + 신규 타석/투구 저장(트랜잭션)
    public void saveAtBat(Long matchId, int gamePk){
        AllPlaysResponse response = mlbApiClient.getAllPlays(gamePk);

        if(response == null || response.allPlays() == null || response.allPlays().isEmpty()){
            log.info("현재 갱신된 타석 데이터가 존재하지 않습니다.");
            return;
        }
        transactionTemplate.executeWithoutResult(status -> persistAtBats(matchId, response));
    }

    private void persistAtBats(Long matchId, AllPlaysResponse response){

        //이미 저장된 타석은 덮어쓰지 않고 건너뜀 (match_id, at_bat_index UNIQUE가 최종 방어)
        //중간에 빠진 타석(재시작 갭)도 채워지도록 마지막 index가 아닌 저장된 index 집합으로 비교
        Set<Integer> savedAtBatIndexes = matchPlaylogRepository.findAtBatIndexesByMatchId(matchId);

        //진행 중인 타석은 결과가 확정되지 않았으므로 저장하지 않음 (저장 시 이후 결과가 갱신되지 않음)
        Predicate<AllPlays> isNewCompletedPlay = play -> play.atBatIndex() != null
                && !savedAtBatIndexes.contains(play.atBatIndex())
                && (play.about() == null || !Boolean.FALSE.equals(play.about().isComplete()));
        List<AllPlays> newPlays = response.allPlays().stream().filter(isNewCompletedPlay).toList();

        List<MatchplayLog> matchplayLogList = newPlays.stream().map(allPlays -> {

            PlayerMaster batter = playerMasterRepository.findByExternalId(allPlays.matchup().batter().id()).orElse(null);

            PlayerMaster pitcher = playerMasterRepository.findByExternalId(allPlays.matchup().pitcher().id()).orElse(null);
            return  MatchplayLog.builder().matchId(matchId).atBatIndex(allPlays.atBatIndex()).batterId(batter==null?null: batter.getId()).batterExternalId(allPlays.matchup().batter().id()).pitcherExternalId(allPlays.matchup().pitcher().id()).
                    pitcherId(pitcher==null? null: pitcher.getId())
                    .resultDescription(allPlays.result() == null ? null : allPlays.result().description())
                    .inning(allPlays.about() == null ? null : allPlays.about().inning())
                    .isTopInning(allPlays.about() == null ? null : allPlays.about().isTopInning())
                    .build();


        }).toList();
        if (matchplayLogList.isEmpty()) {
            log.info("새로 저장할 타석이 없음: matchId={}, 저장된 타석 수={}", matchId, savedAtBatIndexes.size());
            return;
        }

        //saveAll -> matchPlayLogId리턴
        List<MatchplayLog> savedLogs = matchPlaylogRepository.saveAll(matchplayLogList);
        log.info("playLog저장완료");
        //인덱스로 탐색 시간을 줄이기
        Map<Integer, MatchplayLog> saveLogsMap = savedLogs.stream()
                .collect(Collectors.toMap(MatchplayLog::getAtBatIndex, log -> log));

        List<PlayEvent> totalPlayEventList =  newPlays.stream().flatMap(allPlays -> {

            MatchplayLog matchplayLog = saveLogsMap.get(allPlays.atBatIndex());
            if(matchplayLog == null) throw new BusinessException(ErrorCode.MATCH_LOG_NOT_FOUND);
            Long realId = matchplayLog.getId();
            if(allPlays.playEvents() == null) return Stream.empty();
            List<PlayEvent> abBatPlayEventList = allPlays.playEvents().stream().map(playEventsItem -> {

                Details details = playEventsItem.details();
                String event = details == null ? null : details.event();
                String description = details == null ? null : details.description();

                PlayEvent playEvent;

                //자동 볼/스트라이크(피치클락 위반 등)는 pitchData, 구종 정보가 없을 수 있음
                if ("pitch".equals(playEventsItem.type())) {
                    PitchData pitchData = playEventsItem.pitchData();
                    playEvent = PlayEvent.builder().matchPlayLogId(realId)
                            .pitchIndex(playEventsItem.pitchNumber())
                            .event(event)
                            .description(description)
                            .startSpeed(pitchData == null ? null : pitchData.startSpeed())
                            .endSpeed(pitchData == null ? null : pitchData.endSpeed())
                            .pitchTypeDescription(details == null || details.type() == null ? null : details.type().description())
                            .build();
                }
                else playEvent = PlayEvent.builder()
                        .matchPlayLogId(realId)
                        .event(event)
                        .description(description)
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
        redis.save("DefenseLineUp:"+matchId, response, properties.getCurrentPlay().getCurrentPlayCacheDuration());


    }

    private LocalDateTime toUtc(String gameDate){
        return Instant.parse(gameDate).atZone(ZoneId.of("UTC")).toLocalDateTime();
    }

    //이닝 업데이트 (
    private void updateInning(Long matchId, int gamePk){
        saveDefenseLocationToRedis(matchId, gamePk);

    }

}
