package com.mlbbroadcast.team;

import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.teamData.TeamDataResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMasterRepository teamMasterRepository;
    private final MlbApiClient mlbApiClient;
    private final DefaultProperties properties;
    public void updateTeamMaster(){
        Set<TeamMaster> teamMasterList = teamMasterRepository.findAllByIsActive(true);

        TeamDataResponse response =  mlbApiClient.getTeamData();

        List<TeamMaster> newTeamList =
                        response.teams().stream()
                        .filter(team-> teamMasterList.stream().noneMatch(teamMaster -> teamMaster.getExternalId() == team.id()))
                        .map(team-> {//저장되있던 팀 데이터가 아닐경우

                         return TeamMaster.builder().fullName(team.name()).shortName(team.shortName()).division(divisionIdToDivision(team.division().id())).externalId(team.id()).build();
                         }).toList();
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
