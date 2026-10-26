package com.mlbbroadcast.team;

import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.teamData.TeamDataResponse;
import com.mlbbroadcast.external.mlbStatus.dto.teamData.TeamsItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMasterRepository teamMasterRepository;
    private final MlbApiClient mlbApiClient;
    private final DefaultProperties properties;
    private final TransactionTemplate transactionTemplate;

    //없는 팀은 저장하고, 기존 팀은 API 존재 여부로 isActive 정정 (사라진 팀은 비활성화만, 삭제하지 않음)
    //외부 API는 트랜잭션 밖에서 호출하고, DB 정정/저장만 트랜잭션으로 묶음
    public void updateTeamMaster(){
        TeamDataResponse response =  mlbApiClient.getTeamData();
        if(response == null || response.teams() == null || response.teams().isEmpty()){
            //빈 응답으로 전 팀이 비활성화되지 않도록 아무것도 변경하지 않음
            log.warn("팀 데이터 응답이 비어있어 팀 동기화를 건너뜁니다");
            return;
        }
        transactionTemplate.executeWithoutResult(status -> persistTeams(response));
    }

    private void persistTeams(TeamDataResponse response){

        //비활성 팀까지 포함해 조회해야 재등장한 팀이 중복 저장되지 않음
        Map<Integer, TeamMaster> savedTeams = teamMasterRepository.findAll().stream()
                .collect(Collectors.toMap(TeamMaster::getExternalId, Function.identity()));
        Set<Integer> apiTeamIds = response.teams().stream().map(TeamsItem::id).collect(Collectors.toSet());

        savedTeams.forEach((externalId, team) -> {
            if(apiTeamIds.contains(externalId)) team.activate();
            else {
                if(team.isActive()) log.info("API에서 사라진 팀 비활성화: externalId={}", externalId);
                team.deactivate();
            }
        });

        List<TeamMaster> newTeamList =
                        response.teams().stream()
                        .filter(team-> !savedTeams.containsKey(team.id()))
                        .map(team-> TeamMaster.builder().fullName(team.name()).shortName(team.shortName()).division(divisionIdToDivision(team.division().id())).externalId(team.id()).build())
                        .toList();
        if(!newTeamList.isEmpty()) teamMasterRepository.saveAll(newTeamList);

    }

    private Division divisionIdToDivision(int divisionId){
        DefaultProperties.TeamProperties teamProperties = properties.getTeamProperties();

        if(divisionId==teamProperties.getAlEastCode()) return Division.AL_EAST;
        if(divisionId==teamProperties.getAlCentralCode()) return Division.AL_CENTRAL;
        if(divisionId==teamProperties.getAlWestCode()) return Division.AL_WEST;
        if(divisionId==teamProperties.getNlEastCode()) return Division.NL_EAST;
        if(divisionId==teamProperties.getNlCentralCode()) return Division.NL_CENTRAL;
        if(divisionId==teamProperties.getNlWestCode()) return Division.NL_WEST;

        throw new BusinessException(ErrorCode.MLB_STATS_NOT_RESPONSE);
    }
}
