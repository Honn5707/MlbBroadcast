package org.example.mlbbroadcast.match;

import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.teamData.TeamDataResponse;
import com.mlbbroadcast.external.mlbStatus.dto.teamData.TeamsItem;
import com.mlbbroadcast.team.Division;
import com.mlbbroadcast.team.TeamMaster;
import com.mlbbroadcast.team.TeamMasterRepository;
import com.mlbbroadcast.team.TeamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {
    @Mock TeamMasterRepository teamMasterRepository;
    @Mock MlbApiClient mlbApiClient;
    @Mock DefaultProperties properties;
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    @Spy TransactionTemplate transactionTemplate = new TransactionTemplate(txManager);
    @InjectMocks TeamService teamService;

    private TeamMaster team(int externalId, boolean active) {
        TeamMaster team = TeamMaster.builder().fullName("t" + externalId).shortName("t").division(Division.AL_EAST).externalId(externalId).build();
        if (!active) team.deactivate();
        return team;
    }

    private TeamsItem apiTeam(int id) {
        TeamsItem item = mock(TeamsItem.class);
        when(item.id()).thenReturn(id);
        return item;
    }

    @Test
    @DisplayName("API에 있는 팀은 active=true(재등장 팀 재활성화), 사라진 팀은 active=false로 정정하고 삭제하지 않는다")
    void correctActive() {
        TeamMaster stays = team(1, true);
        TeamMaster comesBack = team(2, false);
        TeamMaster disappears = team(3, true);
        when(teamMasterRepository.findAll()).thenReturn(List.of(stays, comesBack, disappears));
        List<TeamsItem> apiTeams = List.of(apiTeam(1), apiTeam(2));
        when(mlbApiClient.getTeamData()).thenReturn(new TeamDataResponse(apiTeams));

        teamService.updateTeamMaster();

        assertThat(stays.isActive()).isTrue();
        assertThat(comesBack.isActive()).isTrue();
        assertThat(disappears.isActive()).isFalse();
        verify(teamMasterRepository, never()).saveAll(anyList()); //재등장 팀을 중복 저장하지 않음
        verify(teamMasterRepository, never()).delete(any());
        InOrder order = inOrder(mlbApiClient, txManager, teamMasterRepository);
        order.verify(mlbApiClient).getTeamData();
        order.verify(txManager).getTransaction(any());
        order.verify(teamMasterRepository).findAll();
        order.verify(txManager).commit(any());
    }

    @Test
    @DisplayName("빈 응답이면 전 팀이 비활성화되지 않도록 아무것도 바꾸지 않는다")
    void emptyResponse() {
        when(mlbApiClient.getTeamData()).thenReturn(new TeamDataResponse(List.of()));

        teamService.updateTeamMaster();

        verify(teamMasterRepository, never()).findAll();
        verifyNoInteractions(txManager);
    }

    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
}
