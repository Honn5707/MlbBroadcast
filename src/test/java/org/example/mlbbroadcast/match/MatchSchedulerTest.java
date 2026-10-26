package org.example.mlbbroadcast.match;

import com.mlbbroadcast.match.MatchScheduler;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.polling.InMemoryJobLock;
import com.mlbbroadcast.match.polling.InMemoryPollingRegistry;
import com.mlbbroadcast.match.polling.PollingPhase;
import com.mlbbroadcast.match.service.MatchStatusService;
import com.mlbbroadcast.match.service.TotalMatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchSchedulerTest {
    @Mock TaskScheduler taskScheduler;
    @Mock TotalMatchService totalMatchService;
    @Mock MatchStatusService matchStatusService;

    InMemoryPollingRegistry registry;
    MatchScheduler scheduler;
    static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    @BeforeEach
    void setUp() {
        registry = new InMemoryPollingRegistry();
        scheduler = new MatchScheduler(taskScheduler, totalMatchService, matchStatusService, registry, new InMemoryJobLock(), new MutableClock(NOW));
    }

    private Matches match(Long id, int gamePk, Instant start) {
        Matches match = Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(MatchStatus.SCHEDULED)
                .scheduledStartedTime(LocalDateTime.ofInstant(start, ZoneOffset.UTC)).seasonYear("2026").gamePk(gamePk).build();
        ReflectionTestUtils.setField(match, "id", id);
        return match;
    }

    @Test
    @DisplayName("[10-2] 같은 경기를 두 번 등록해도(신규 저장 + 복구 + 일일 배치) 시작 스케쥴은 하나만 등록된다")
    void scheduleOnlyOnce() {
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        assertThat(scheduler.scheduleMatchStart(match(1L, 777, NOW.plusSeconds(3600)))).isTrue();
        assertThat(scheduler.scheduleMatchStart(match(1L, 777, NOW.plusSeconds(3600)))).isFalse();

        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        verify(matchStatusService, times(1)).recordPollingPhase(1L, PollingPhase.WAITING_START);
    }

    @Test
    @DisplayName("시작 시각이 지난 경기(재시작 따라잡기)는 즉시 status 폴링을 등록한다")
    void pastStartPollsImmediately() {
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        scheduler.scheduleMatchStart(match(1L, 777, NOW.minusSeconds(3600)));

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(registry.phaseOf(777)).contains(PollingPhase.STATUS);
    }

    @Test
    @DisplayName("이미 폴링 중인 경기는 시작 스케쥴을 다시 등록하지 않는다")
    void skipWhenAlreadyPolling() {
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        scheduler.startLivePolling(1L, 777);

        assertThat(scheduler.scheduleMatchStart(match(1L, 777, NOW.plusSeconds(3600)))).isFalse();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("status 폴링 -> 타석 폴링 전환 시 이전 작업은 취소된다")
    void switchingPhaseCancelsPrevious() {
        ScheduledFuture<?> status = mock(ScheduledFuture.class);
        ScheduledFuture<?> live = mock(ScheduledFuture.class);
        doReturn(status, live).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        scheduler.startStatusPolling(1L, 777);
        scheduler.startLivePolling(1L, 777);

        verify(status).cancel(false);
        verify(live, never()).cancel(false);
        assertThat(registry.phaseOf(777)).contains(PollingPhase.LIVE_PLAY);
        verify(matchStatusService).recordPollingPhase(1L, PollingPhase.LIVE_PLAY);
    }

    @Test
    @DisplayName("폴링 종료 시 대기 중인 시작 스케쥴도 취소되고 DB 폴링 단계는 null")
    void endPollingCancelsPending() {
        ScheduledFuture<?> pending = mock(ScheduledFuture.class);
        doReturn(pending).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        scheduler.scheduleMatchStart(match(1L, 777, NOW.plusSeconds(3600)));

        scheduler.endPolling(1L, 777);

        verify(pending).cancel(false);
        assertThat(registry.phaseOf(777)).isEmpty();
        verify(matchStatusService).recordPollingPhase(1L, null);
    }
}
