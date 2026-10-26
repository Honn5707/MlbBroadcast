package com.mlbbroadcast.player;

import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.retry.MlbApiRetryExecutor;
import com.mlbbroadcast.external.mlbStatus.dto.playerData.PlayerDataResponse;
import com.mlbbroadcast.external.mlbStatus.dto.playerData.RosterItem;
import com.mlbbroadcast.player.entity.PlayerMaster;
import com.mlbbroadcast.player.repository.PlayerMasterRepository;
import com.mlbbroadcast.team.TeamMaster;
import com.mlbbroadcast.team.TeamMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {
    private final MlbApiClient mlbApiClient;
    private final TeamMasterRepository teamMasterRepository;
    private final PlayerMasterRepository playerMasterRepository;
    private final TransactionTemplate transactionTemplate;

    //1) 활성 팀 로스터를 외부 API로 모두 받아온 뒤(트랜잭션 밖, 팀 수만큼 호출) 2) 선수 저장/팀 변경만 트랜잭션으로 묶음
    public void updatePlayerMaster(){
        Set<TeamMaster> teamMasters = teamMasterRepository.findAllByIsActive(true);
        //팀 내부 id -> 로스터
        Map<Long, List<RosterItem>> rosters = new LinkedHashMap<>();
        for(TeamMaster team: teamMasters) {
            PlayerDataResponse response;
            try {
                response = mlbApiClient.getPlayerData(team.getExternalId());
            }catch (Exception e){
                //일시 장애는 작업 전체를 재시도 예약하도록 전파, 그 외(4xx 등)는 해당 팀만 건너뜀
                if(MlbApiRetryExecutor.isRetryable(e)) throw e;
                log.warn("팀 로스터 조회 실패: team={}", team.getId(), e);
                continue;
            }
            if(response == null || response.roster() == null){
                log.warn("팀 로스터 응답이 비어있습니다: team={}", team.getId());
                continue;
            }
            rosters.put(team.getId(), response.roster());
        }

        transactionTemplate.executeWithoutResult(status -> persistPlayers(rosters));
    }

    private void persistPlayers(Map<Long, List<RosterItem>> rosters){
        Map<Integer, PlayerMaster> playerList = playerMasterRepository.findAll().stream().collect(Collectors.toMap(PlayerMaster::getExternalId, p->p));
        List<PlayerMaster> updatePlayerMasterList = new ArrayList<>();
        rosters.forEach((teamId, roster) -> {
            for (RosterItem rosterItem : roster) {
                PlayerMaster player = playerList.get(rosterItem.person().id());
                if (player == null) {
                    PlayerMaster newPlayer = createPlayerMasterEntity(rosterItem, teamId);
                    updatePlayerMasterList.add(newPlayer);
                    playerList.put(rosterItem.person().id(), newPlayer);

                }
                else if (!player.getTeamId().equals(teamId)) player.changeTeamId(teamId);
            }
        });

        playerMasterRepository.saveAll(updatePlayerMasterList);


    }

    private PlayerMaster createPlayerMasterEntity(RosterItem rosterItem, Long teamId){
        return PlayerMaster.builder()
                .name(rosterItem.person().fullName())
                .teamId(teamId)
                .position(rosterItem.position().code())
                .jerseyNumber(parseJersey(rosterItem.jerseyNumber()))
                .externalId(rosterItem.person().id())
                .build();

    }

    private Integer parseJersey(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.valueOf(s.trim());     // 문자열 → Integer 변환은 valueOf/parseInt
        } catch (NumberFormatException e) {
            return null;                          // 숫자가 아닌 값은 등번호 없음으로 처리
        }
    }
}
