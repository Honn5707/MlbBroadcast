package org.example.mlbbroadcast.match;

import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.allPlays.*;
import com.mlbbroadcast.external.mlbStatus.dto.batterOrderLineUp.LineUpResponse;
import com.mlbbroadcast.external.mlbStatus.dto.scheduled.DatesItem;
import com.mlbbroadcast.external.mlbStatus.dto.scheduled.GamesItem;
import com.mlbbroadcast.external.mlbStatus.dto.scheduled.ScheduledListResponse;
import com.mlbbroadcast.match.entities.LineUp;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.entities.MatchplayLog;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.event.MatchScheduledSaveEvent;
import com.mlbbroadcast.match.repositories.*;
import com.mlbbroadcast.match.service.SaveMatchService;
import com.mlbbroadcast.player.entity.PlayerMaster;
import com.mlbbroadcast.player.repository.PlayerMasterRepository;
import com.mlbbroadcast.team.TeamMasterRepository;
import com.mlbbroadcast.util.RedisUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaveMatchServiceTest {
    @Mock MlbApiClient mlbApiClient;
    @Mock PlayerMasterRepository playerMasterRepository;
    @Mock MatchPlaylogRepository matchPlaylogRepository;
    @Mock PlayEventRepository playEventRepository;
    @Mock MatchesRepository matchesRepository;
    @Mock LineUpRepository lineUpRepository;
    @Mock TeamMasterRepository teamMasterRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock DefaultProperties properties;
    @Mock RedisUtilities redis;
    //실제 TransactionTemplate + 가짜 트랜잭션 매니저: 트랜잭션 시작/커밋 시점을 검증
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    @Spy TransactionTemplate transactionTemplate = new TransactionTemplate(txManager);
    @InjectMocks SaveMatchService service;

    static final Long MATCH_ID = 1L;
    static final int GAME_PK = 777;

    private AllPlays play(int atBatIndex, boolean complete) {
        return new AllPlays(new Result("single", null, null), null, null, new About(1, atBatIndex, true, complete), null,
                atBatIndex, null, List.of(), new Matchup(new Batter("b", 100), new Pitcher("p", 200)));
    }

    @SuppressWarnings("unchecked")
    private List<Integer> savedAtBatIndexes() {
        ArgumentCaptor<List<MatchplayLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(matchPlaylogRepository).saveAll(captor.capture());
        return captor.getValue().stream().map(MatchplayLog::getAtBatIndex).toList();
    }

    private void echoSaveAll() {
        when(matchPlaylogRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<MatchplayLog> logs = invocation.getArgument(0);
            long id = 1;
            for (MatchplayLog log : logs) ReflectionTestUtils.setField(log, "id", id++);
            return logs;
        });
    }

    @Test
    @DisplayName("[10-3] 이미 저장된 타석은 다시 저장하지 않는다 (같은 타석 두 번 저장 시도 시 한 행)")
    void skipSavedAtBats() {
        when(mlbApiClient.getAllPlays(GAME_PK)).thenReturn(new AllPlaysResponse(List.of(play(0, true), play(1, true))));
        when(matchPlaylogRepository.findAtBatIndexesByMatchId(MATCH_ID)).thenReturn(Set.of(0, 1));

        service.saveAtBat(MATCH_ID, GAME_PK);

        verify(matchPlaylogRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("[10-8] 중간에 빠진 타석(재시작 갭)도 채운다")
    void backfillGap() {
        when(mlbApiClient.getAllPlays(GAME_PK)).thenReturn(new AllPlaysResponse(List.of(play(0, true), play(1, true), play(2, true), play(3, true))));
        when(matchPlaylogRepository.findAtBatIndexesByMatchId(MATCH_ID)).thenReturn(Set.of(0, 2));
        echoSaveAll();

        service.saveAtBat(MATCH_ID, GAME_PK);

        assertThat(savedAtBatIndexes()).containsExactly(1, 3);
    }

    @Test
    @DisplayName("진행 중인(미완료) 타석은 저장하지 않는다")
    void skipIncompleteAtBat() {
        when(mlbApiClient.getAllPlays(GAME_PK)).thenReturn(new AllPlaysResponse(List.of(play(0, true), play(1, false))));
        when(matchPlaylogRepository.findAtBatIndexesByMatchId(MATCH_ID)).thenReturn(Set.of());
        echoSaveAll();

        service.saveAtBat(MATCH_ID, GAME_PK);

        assertThat(savedAtBatIndexes()).containsExactly(0);
    }

    @Test
    @DisplayName("마스터에 없는 선수는 *_id null, external_id는 보존한다")
    void unknownPlayerKeepsExternalId() {
        when(mlbApiClient.getAllPlays(GAME_PK)).thenReturn(new AllPlaysResponse(List.of(play(0, true))));
        when(matchPlaylogRepository.findAtBatIndexesByMatchId(MATCH_ID)).thenReturn(Set.of());
        when(playerMasterRepository.findByExternalId(anyInt())).thenReturn(Optional.empty());
        echoSaveAll();

        service.saveAtBat(MATCH_ID, GAME_PK);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchplayLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(matchPlaylogRepository).saveAll(captor.capture());
        MatchplayLog saved = captor.getValue().getFirst();
        assertThat(saved.getBatterId()).isNull();
        assertThat(saved.getBatterExternalId()).isEqualTo(100);
        assertThat(saved.getPitcherExternalId()).isEqualTo(200);
    }

    @Test
    @DisplayName("라인업: 마스터에 없는 선수도 player_id null로 저장하고, 팀은 경기의 홈/원정 기준")
    void lineUpWithUnknownPlayer() {
        Matches match = Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(MatchStatus.LIVE)
                .scheduledStartedTime(LocalDateTime.now()).gamePk(GAME_PK).build();
        when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        LineUpResponse response = mock(LineUpResponse.class, RETURNS_DEEP_STUBS);
        when(response.teams().home().battingOrder()).thenReturn(List.of(1, 2));
        when(response.teams().away().battingOrder()).thenReturn(List.of(3));
        when(mlbApiClient.getLineUp(GAME_PK)).thenReturn(response);
        PlayerMaster known = PlayerMaster.builder().teamId(99L).externalId(1).name("a").position("P").build();
        ReflectionTestUtils.setField(known, "id", 50L);
        when(playerMasterRepository.findByExternalId(1)).thenReturn(Optional.of(known));
        when(playerMasterRepository.findByExternalId(2)).thenReturn(Optional.empty());
        when(playerMasterRepository.findByExternalId(3)).thenReturn(Optional.empty());

        service.saveBattingOrderLineUp(MATCH_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LineUp>> captor = ArgumentCaptor.forClass(List.class);
        verify(lineUpRepository).saveAll(captor.capture());
        List<LineUp> lineUps = captor.getValue();
        assertThat(lineUps).extracting(LineUp::getPlayerId).containsExactly(50L, null, null);
        assertThat(lineUps).extracting(LineUp::getTeamId).containsExactly(10L, 10L, 20L);
        assertThat(lineUps).extracting(LineUp::getExternalId).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("일정 동기화에서 MATCH_SUSPENDED_UNKNOWN 경기를 다시 만나면 시작 시각 갱신 후 폴링 재등록")
    void reRegisterSuspendedUnknown() {
        Matches suspended = Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(MatchStatus.MATCH_SUSPENDED_UNKNOWN)
                .scheduledStartedTime(LocalDateTime.of(2026, 10, 8, 23, 0)).gamePk(GAME_PK).build();
        Matches finished = Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(MatchStatus.FINAL)
                .scheduledStartedTime(LocalDateTime.of(2026, 10, 9, 1, 0)).gamePk(888).build();
        ReflectionTestUtils.setField(suspended, "id", 5L);
        ReflectionTestUtils.setField(finished, "id", 6L);
        when(matchesRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(suspended));
        GamesItem suspendedGame = mock(GamesItem.class);
        when(suspendedGame.gamePk()).thenReturn(GAME_PK);
        when(suspendedGame.gameDate()).thenReturn("2026-10-09T17:05:00Z");
        GamesItem finishedGame = mock(GamesItem.class);
        when(finishedGame.gamePk()).thenReturn(888);
        when(mlbApiClient.getListOfSchedule(any())).thenReturn(new ScheduledListResponse(null, null, null, null, null,
                List.of(new DatesItem("2026-10-09", null, null, null, null, List.of(suspendedGame, finishedGame)))));
        when(teamMasterRepository.findAllByIsActive(true)).thenReturn(Set.of());
        when(matchesRepository.findAllByGamePkIn(anyList())).thenReturn(List.of(suspended, finished));

        service.saveScheduledMatches(LocalDate.of(2026, 10, 9));

        assertThat(suspended.getScheduledStartedTime()).isEqualTo(LocalDateTime.of(2026, 10, 9, 17, 5));
        verify(eventPublisher).publishEvent(new MatchScheduledSaveEvent(suspended));
        verify(eventPublisher, never()).publishEvent(new MatchScheduledSaveEvent(finished));
        verify(matchesRepository, never()).saveAll(anyList());
        verify(matchesRepository).findByIdForUpdate(5L);           //수정 대상만 락
        verify(matchesRepository, never()).findByIdForUpdate(6L);
    }

    @Test
    @DisplayName("[락] 락을 잡고 다시 읽었을 때 이미 다른 상태로 바뀌었으면 재등록하지 않는다")
    void skipRescheduleWhenChangedWhileWaitingLock() {
        Matches staleRead = Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(MatchStatus.MATCH_SUSPENDED_UNKNOWN)
                .scheduledStartedTime(LocalDateTime.of(2026, 10, 8, 23, 0)).gamePk(GAME_PK).build();
        Matches lockedRead = Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(MatchStatus.FINAL)
                .scheduledStartedTime(LocalDateTime.of(2026, 10, 8, 23, 0)).gamePk(GAME_PK).build();
        ReflectionTestUtils.setField(staleRead, "id", 5L);
        GamesItem game = mock(GamesItem.class);
        when(game.gamePk()).thenReturn(GAME_PK);
        when(game.gameDate()).thenReturn("2026-10-09T17:05:00Z");
        when(mlbApiClient.getListOfSchedule(any())).thenReturn(new ScheduledListResponse(null, null, null, null, null,
                List.of(new DatesItem("2026-10-09", null, null, null, null, List.of(game)))));
        when(teamMasterRepository.findAllByIsActive(true)).thenReturn(Set.of());
        when(matchesRepository.findAllByGamePkIn(anyList())).thenReturn(List.of(staleRead));
        when(matchesRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(lockedRead));

        service.saveScheduledMatches(LocalDate.of(2026, 10, 9));

        assertThat(lockedRead.getScheduledStartedTime()).isEqualTo(LocalDateTime.of(2026, 10, 8, 23, 0));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("[트랜잭션 경계] 타석: 외부 API 호출 -> 트랜잭션 시작 -> 조회/저장 -> 커밋 순서")
    void atBatApiOutsideTransaction() {
        when(mlbApiClient.getAllPlays(GAME_PK)).thenReturn(new AllPlaysResponse(List.of(play(0, true))));
        when(matchPlaylogRepository.findAtBatIndexesByMatchId(MATCH_ID)).thenReturn(Set.of());
        echoSaveAll();

        service.saveAtBat(MATCH_ID, GAME_PK);

        InOrder order = inOrder(mlbApiClient, txManager, matchPlaylogRepository);
        order.verify(mlbApiClient).getAllPlays(GAME_PK);
        order.verify(txManager).getTransaction(any());
        order.verify(matchPlaylogRepository).findAtBatIndexesByMatchId(MATCH_ID);
        order.verify(matchPlaylogRepository).saveAll(anyList());
        order.verify(txManager).commit(any());
    }

    @Test
    @DisplayName("[트랜잭션 경계] 응답이 비어 있으면 트랜잭션을 시작하지 않는다")
    void noTransactionForEmptyResponse() {
        when(mlbApiClient.getAllPlays(GAME_PK)).thenReturn(new AllPlaysResponse(List.of()));
        when(mlbApiClient.getListOfSchedule(any())).thenReturn(new ScheduledListResponse(null, null, null, null, null, List.of()));

        service.saveAtBat(MATCH_ID, GAME_PK);
        service.saveScheduledMatches(LocalDate.of(2026, 10, 12));

        verifyNoInteractions(txManager);
    }

    @Test
    @DisplayName("[트랜잭션 경계] API가 실패하면 트랜잭션을 시작하지 않는다 (DB 커넥션 점유 없음)")
    void apiFailureBeforeTransaction() {
        when(mlbApiClient.getAllPlays(GAME_PK)).thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveAtBat(MATCH_ID, GAME_PK))
                .isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
        verifyNoInteractions(txManager);
    }

    @Test
    @DisplayName("[트랜잭션 경계] 일정: 외부 API 호출 후 트랜잭션 안에서 기존 경기 조회/저장")
    void scheduleApiOutsideTransaction() {
        GamesItem game = mock(GamesItem.class);
        when(game.gamePk()).thenReturn(GAME_PK);
        when(mlbApiClient.getListOfSchedule(any())).thenReturn(new ScheduledListResponse(null, null, null, null, null,
                List.of(new DatesItem("2026-10-12", null, null, null, null, List.of(game)))));
        when(teamMasterRepository.findAllByIsActive(true)).thenReturn(Set.of());
        Matches saved = Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(MatchStatus.FINAL)
                .scheduledStartedTime(LocalDateTime.of(2026, 10, 12, 0, 0)).gamePk(GAME_PK).build();
        when(matchesRepository.findAllByGamePkIn(anyList())).thenReturn(List.of(saved));

        service.saveScheduledMatches(LocalDate.of(2026, 10, 12));

        InOrder order = inOrder(mlbApiClient, txManager, matchesRepository);
        order.verify(mlbApiClient).getListOfSchedule(any());
        order.verify(txManager).getTransaction(any());
        order.verify(matchesRepository).findAllByGamePkIn(anyList());
        order.verify(txManager).commit(any());
    }

    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
}
