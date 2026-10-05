package com.mlbbroadcast.player;

import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.playerData.PlayerDataResponse;
import com.mlbbroadcast.external.mlbStatus.dto.playerData.RosterItem;
import com.mlbbroadcast.player.entity.PlayerMaster;
import com.mlbbroadcast.player.repository.PlayerMasterRepository;
import com.mlbbroadcast.team.TeamMaster;
import com.mlbbroadcast.team.TeamMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    @Transactional
    public void updatePlayerMaster(){
        Set<TeamMaster> teamMasters = teamMasterRepository.findAllByIsActive(true);
        Map<Integer, PlayerMaster> playerList = playerMasterRepository.findAll().stream().collect(Collectors.toMap(PlayerMaster::getExternalId, p->p));
        //화설화 되어있는 팀 로스터등록 팀 마스터 (id)-> 팀 로스터 순회
        List<PlayerMaster> updatePlayerMasterList = new ArrayList<>();
        for(TeamMaster team: teamMasters) {
            Long teamId =  team.getId();
            PlayerDataResponse response;
            try {
                response = mlbApiClient.getPlayerData(team.getExternalId());
            }catch (Exception e){log.warn("팀 로스터 조회 실패: team={}", team.getId(), e); continue;}

            for (RosterItem roster : response.roster()) {
                PlayerMaster player = playerList.get(roster.person().id());
                if (player == null) {
                    PlayerMaster newPlayer = createPlayerMasterEntity(roster, teamId);
                    updatePlayerMasterList.add(newPlayer);
                    playerList.put(roster.person().id(), newPlayer);

                }
                else if (!player.getTeamId().equals(teamId)) player.changeTeamId(teamId);
            }
        }

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
