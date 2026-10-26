package org.example.mlbbroadcast.match;

import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlay;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlayResponse;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.GameData;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.GameStatusResponse;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.LiveData;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.Status;
import com.mlbbroadcast.external.mlbStatus.retry.MlbApiRetryExecutor;
import org.springframework.scheduling.TaskScheduler;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.event.*;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.match.service.MatchStatusService;
import com.mlbbroadcast.match.service.SaveMatchService;
import com.mlbbroadcast.match.service.TotalMatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TotalMatchServiceTest {
    @Mock MlbApiClient mlbApiClient;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock MatchesRepository matchesRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock SaveMatchService saveMatchService;

    TotalMatchService service;
    MutableClock clock;

    static final Long MATCH_ID = 1L;
    static final int GAME_PK = 777;
    static final Instant T0 = Instant.parse("2026-10-09T23:00:00Z");

    @BeforeEach
    void setUp() {
        DefaultProperties properties = new DefaultProperties();
        properties.getCurrentPlay().setGameStatusCheckInterval(Duration.ofMinutes(5));
        properties.getPlayInfo().setMatchStartPollingLimit(Duration.ofHours(12));
        properties.getPlayInfo().setDelayBeforeStartLimit(Duration.ofHours(12));
        properties.getPlayInfo().setDelayInGameLimit(Duration.ofHours(6));
        properties.getMlbApiRetry().setInitialDelay(Duration.ofSeconds(5));
        properties.getMlbApiRetry().setMaxDelay(Duration.ofSeconds(60));
        properties.getMlbApiRetry().setMaxAttempts(5);
        clock = new MutableClock(T0);
        MlbApiRetryExecutor retryExecutor = new MlbApiRetryExecutor(mock(TaskScheduler.class), properties, clock);
        service = new TotalMatchService(mlbApiClient, properties, messagingTemplate, matchesRepository,
                eventPublisher, saveMatchService, new MatchStatusService(matchesRepository), retryExecutor, clock);
    }

    // ---------- fixtures ----------

    private Matches match(MatchStatus status, LocalDateTime scheduledStart) {
        Matches match = Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(status)
                .scheduledStartedTime(scheduledStart).seasonYear("2026").gamePk(GAME_PK).build();
        ReflectionTestUtils.setField(match, "id", MATCH_ID);
        when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        when(matchesRepository.findByIdForUpdate(MATCH_ID)).thenReturn(Optional.of(match));
        return match;
    }

    private Matches scheduledMatch() {
        return match(MatchStatus.SCHEDULED, utc(T0).minusMinutes(1));
    }

    private Matches liveMatch() {
        Matches match = match(MatchStatus.LIVE, utc(T0).minusHours(1));
        match.matchStartTimeSet(utc(T0).minusHours(1));
        return match;
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private void apiStatus(String abstractState, String detailedState) {
        when(mlbApiClient.getGameStatus(GAME_PK))
                .thenReturn(new GameStatusResponse(new GameData(null, new Status(null, abstractState, detailedState)), null));
    }

    private void apiFinal(Integer home, Integer away) {
        LiveData liveData = new LiveData(new LiveData.Linescore(new LiveData.Teams(new LiveData.TeamScore(home), new LiveData.TeamScore(away))));
        when(mlbApiClient.getGameStatus(GAME_PK))
                .thenReturn(new GameStatusResponse(new GameData(null, new Status(null, "Final", "Final")), liveData));
    }

    private CurrentPlay play(Integer atBatIndex) {
        return new CurrentPlay(null, null, null, atBatIndex, null, null, null);
    }

    //같은 타석 응답을 두 번 받아 캐시를 채운 뒤, 무갱신 시간을 흘려 status 재확인을 유도
    private void staleLivePolling() {
        when(mlbApiClient.getCurrentPlay(GAME_PK)).thenReturn(new CurrentPlayResponse(play(5)));
        service.fetchCurrentPlayData(MATCH_ID, GAME_PK);
        clock.advance(Duration.ofMinutes(5));
    }

    // ---------- tests ----------

    @Nested
    @DisplayName("정상 흐름")
    class NormalFlow {

        @Test
        @DisplayName("[10-1] SCHEDULED -> LIVE -> FINAL: 승리팀/점수/종료시각 저장, FINAL 이벤트 1회")
        void scheduledToLiveToFinal() {
            Matches match = scheduledMatch();

            apiStatus("Live", "In Progress");
            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.LIVE);
            assertThat(match.getRealStartedTime()).isNotNull();
            verify(saveMatchService).saveBattingOrderLineUp(MATCH_ID);
            verify(eventPublisher).publishEvent(new MatchStartedEvent(MATCH_ID, GAME_PK));

            staleLivePolling();
            apiFinal(5, 3);
            service.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.FINAL);
            assertThat(match.getHomeTeamScore()).isEqualTo(5);
            assertThat(match.getVisitTeamScore()).isEqualTo(3);
            assertThat(match.getWinSide()).isEqualTo(Side.HOME);
            assertThat(match.getEndedTime()).isNotNull();
            verify(eventPublisher, times(1)).publishEvent(new MatchFinalizedEvent(MATCH_ID));
            verify(eventPublisher).publishEvent(new MatchEndedPollingEvent(MATCH_ID, GAME_PK));

            //중복 호출돼도 FINAL 이후 불변, 이벤트 재발행 없음
            service.checkMatchStatus(MATCH_ID, GAME_PK);
            verify(eventPublisher, times(1)).publishEvent(new MatchFinalizedEvent(MATCH_ID));
            assertThat(match.getHomeTeamScore()).isEqualTo(5);
        }

        @Test
        @DisplayName("라이브 데이터는 /topic/matches/{matchId}로 전송한다")
        void broadcastDestination() {
            liveMatch();
            CurrentPlay response = play(3);
            when(mlbApiClient.getCurrentPlay(GAME_PK)).thenReturn(new CurrentPlayResponse(response));

            service.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            verify(messagingTemplate).convertAndSend("/topic/matches/" + MATCH_ID, response);
        }

        @Test
        @DisplayName("무갱신 시간이 지나기 전에는 status를 조회하지 않는다")
        void notStaleYet() {
            liveMatch();
            when(mlbApiClient.getCurrentPlay(GAME_PK)).thenReturn(new CurrentPlayResponse(play(5)));
            service.fetchCurrentPlayData(MATCH_ID, GAME_PK);
            clock.advance(Duration.ofMinutes(4));

            service.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            verify(mlbApiClient, never()).getGameStatus(anyInt());
        }

        @Test
        @DisplayName("무갱신 후 status가 LIVE면 아무것도 바꾸지 않는다 (5분 무갱신은 종료 판정이 아님)")
        void staleButStillLive() {
            Matches match = liveMatch();
            staleLivePolling();
            apiStatus("Live", "In Progress");

            service.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.LIVE);
            verify(eventPublisher, never()).publishEvent(any(MatchEndedPollingEvent.class));
        }
    }

    @Nested
    @DisplayName("지연(DELAYED)")
    class Delayed {

        @Test
        @DisplayName("[10-4] LIVE 중 지연 -> status 폴링 전환 -> LIVE 복귀 시 현재 타석 저장 후 타석 폴링 재개")
        void delayedThenLive() {
            Matches match = liveMatch();
            staleLivePolling();
            apiStatus("Live", "Delayed");

            service.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.DELAYED);
            assertThat(match.getDelayedSince()).isNotNull();
            verify(eventPublisher).publishEvent(new MatchStatusPollingEvent(MATCH_ID, GAME_PK));

            apiStatus("Live", "In Progress");
            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.LIVE);
            assertThat(match.getDelayedSince()).isNull();
            verify(saveMatchService, times(2)).saveAtBat(MATCH_ID, GAME_PK); //캐시 최초 채움 + 복귀 시 저장
            verify(saveMatchService, never()).saveBattingOrderLineUp(anyLong()); //재개이므로 라인업 재저장 없음
            verify(eventPublisher).publishEvent(new MatchStartedEvent(MATCH_ID, GAME_PK));
        }

        @Test
        @DisplayName("[10-5] 시작 전 DELAYED가 12시간을 넘으면 MATCH_SUSPENDED_UNKNOWN + 이벤트 + 폴링 종료")
        void delayedBeforeStartOver12h() {
            Matches match = scheduledMatch();
            apiStatus("Preview", "Delayed Start");

            service.checkMatchStatus(MATCH_ID, GAME_PK);
            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.DELAYED);

            clock.advance(Duration.ofHours(11));
            service.checkMatchStatus(MATCH_ID, GAME_PK);
            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.DELAYED);

            clock.advance(Duration.ofHours(1).plusMinutes(1));
            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.MATCH_SUSPENDED_UNKNOWN);
            verify(eventPublisher).publishEvent(new MatchSuspendedUnknownEvent(MATCH_ID));
            verify(eventPublisher).publishEvent(new MatchEndedPollingEvent(MATCH_ID, GAME_PK));
        }

        @Test
        @DisplayName("[10-6] LIVE 중 지연이 6시간을 넘으면 MATCH_SUSPENDED_UNKNOWN")
        void delayedInGameOver6h() {
            Matches match = liveMatch();
            staleLivePolling();
            apiStatus("Live", "Delayed");
            service.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            clock.advance(Duration.ofHours(5));
            service.checkMatchStatus(MATCH_ID, GAME_PK);
            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.DELAYED);

            clock.advance(Duration.ofHours(1).plusMinutes(1));
            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.MATCH_SUSPENDED_UNKNOWN);
            verify(eventPublisher).publishEvent(new MatchSuspendedUnknownEvent(MATCH_ID));
        }

        @Test
        @DisplayName("지연 중 FINAL(콜드게임 등)이면 DELAYED -> FINAL 확정")
        void delayedThenFinal() {
            Matches match = liveMatch();
            staleLivePolling();
            apiStatus("Live", "Delayed");
            service.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            apiFinal(2, 2);
            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.FINAL);
            assertThat(match.getWinSide()).isEqualTo(Side.NONE);
            verify(eventPublisher).publishEvent(new MatchFinalizedEvent(MATCH_ID));
        }
    }

    @Nested
    @DisplayName("따라잡기 / 예외 상황")
    class CatchUpAndFailures {

        @Test
        @DisplayName("[10-7] 서버 다운 중 끝난 경기: SCHEDULED에서 바로 FINAL이면 타석 backfill 후 확정")
        void catchUpFinal() {
            Matches match = match(MatchStatus.SCHEDULED, utc(T0).minusHours(5));
            apiFinal(1, 4);

            service.checkMatchStatus(MATCH_ID, GAME_PK);

            verify(saveMatchService).saveAtBat(MATCH_ID, GAME_PK);
            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.FINAL);
            assertThat(match.getWinSide()).isEqualTo(Side.VISIT);
            verify(eventPublisher).publishEvent(new MatchFinalizedEvent(MATCH_ID));
        }

        @Test
        @DisplayName("[10-8] 재시작 후 LIVE 경기: 갭 backfill(타석 저장) 후 타석 폴링 재개, 라인업은 다시 저장하지 않음")
        void catchUpLive() {
            Matches match = liveMatch();
            apiStatus("Live", "In Progress");

            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.LIVE);
            verify(saveMatchService).saveAtBat(MATCH_ID, GAME_PK);
            verify(saveMatchService, never()).saveBattingOrderLineUp(anyLong());
            verify(eventPublisher).publishEvent(new MatchStartedEvent(MATCH_ID, GAME_PK));
        }

        @Test
        @DisplayName("FINAL 응답에 점수가 없으면 확정을 보류하고 폴링을 유지한다")
        void finalWithoutScore() {
            Matches match = liveMatch();
            apiStatus("Final", "Final");

            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.LIVE);
            verify(eventPublisher, never()).publishEvent(any(MatchFinalizedEvent.class));
            verify(eventPublisher, never()).publishEvent(any(MatchEndedPollingEvent.class));
        }

        @Test
        @DisplayName("FINAL 직전 타석 저장이 실패하면 확정을 보류한다 (타석 누락 상태로 확정 방지)")
        void finalButAtBatSaveFails() {
            Matches match = liveMatch();
            apiFinal(3, 1);
            doThrow(new RuntimeException("api error")).when(saveMatchService).saveAtBat(MATCH_ID, GAME_PK);

            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.LIVE);
            verify(eventPublisher, never()).publishEvent(any(MatchFinalizedEvent.class));
        }

        @Test
        @DisplayName("시작 전 취소/연기면 CANCELED + 이벤트 + 폴링 종료")
        void canceled() {
            Matches match = scheduledMatch();
            apiStatus("Final", "Postponed");

            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.CANCELED);
            verify(eventPublisher).publishEvent(new MatchCanceledEvent(MATCH_ID));
            verify(eventPublisher).publishEvent(new MatchEndedPollingEvent(MATCH_ID, GAME_PK));
            verify(saveMatchService, never()).saveAtBat(anyLong(), anyInt());
        }

        @Test
        @DisplayName("[10-14] 허용되지 않은 전이(LIVE -> CANCELED)는 거부하고 폴링을 유지한다")
        void rejectedTransition() {
            Matches match = liveMatch();
            apiStatus("Final", "Cancelled");

            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.LIVE);
            verify(eventPublisher, never()).publishEvent(any(MatchCanceledEvent.class));
            verify(eventPublisher, never()).publishEvent(any(MatchEndedPollingEvent.class));
        }

        @Test
        @DisplayName("[10-14] 이미 FINAL인 경기는 API를 호출하지 않고 폴링만 종료한다")
        void alreadyFinal() {
            match(MatchStatus.FINAL, utc(T0).minusHours(5));

            service.checkMatchStatus(MATCH_ID, GAME_PK);

            verify(mlbApiClient, never()).getGameStatus(anyInt());
            verify(eventPublisher).publishEvent(new MatchEndedPollingEvent(MATCH_ID, GAME_PK));
        }

        @Test
        @DisplayName("경기를 찾을 수 없으면 해당 gamePk 폴링을 종료한다")
        void matchNotFound() {
            when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.empty());

            service.checkMatchStatus(MATCH_ID, GAME_PK);

            verify(eventPublisher).publishEvent(new MatchEndedPollingEvent(MATCH_ID, GAME_PK));
        }

        @Test
        @DisplayName("status 응답이 비어 있으면 상태를 바꾸지 않고 다음 폴링을 기다린다")
        void emptyStatusResponse() {
            Matches match = scheduledMatch();
            when(mlbApiClient.getGameStatus(GAME_PK)).thenReturn(null);

            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.SCHEDULED);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("시작 예정 후 12시간이 지나도 Preview면 폴링을 종료한다 (상태는 SCHEDULED 유지)")
        void startPollingExpired() {
            Matches match = match(MatchStatus.SCHEDULED, utc(T0).minusHours(13));
            apiStatus("Preview", "Scheduled");

            service.checkMatchStatus(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.SCHEDULED);
            verify(eventPublisher).publishEvent(new MatchEndedPollingEvent(MATCH_ID, GAME_PK));
        }

        @Test
        @DisplayName("일정 저장: 하루가 실패해도 나머지 날짜는 계속 저장한다")
        void scheduleContinuesOnFailure() {
            LocalDate today = LocalDate.of(2026, 10, 9);
            doThrow(new RuntimeException("api error")).when(saveMatchService).saveScheduledMatches(today.plusDays(3));

            service.updateScheduleListForMatch(today);

            verify(saveMatchService, times(15)).saveScheduledMatches(any(LocalDate.class));
        }

        @Test
        @DisplayName("일정 저장: 미국 기준 전날(officialDate) 일정부터 조회한다 (UTC 오늘 새벽 시작 경기 누락 방지)")
        void scheduleIncludesUsYesterday() {
            LocalDate utcToday = LocalDate.of(2026, 10, 12);

            service.updateScheduleListForMatch(utcToday);

            verify(saveMatchService).saveScheduledMatches(LocalDate.of(2026, 10, 11));
            verify(saveMatchService).saveScheduledMatches(LocalDate.of(2026, 10, 25));
            verify(saveMatchService, never()).saveScheduledMatches(LocalDate.of(2026, 10, 10));
        }
    }
}
