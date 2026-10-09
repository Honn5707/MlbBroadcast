package org.example.mlbbroadcast;

import com.mlbbroadcast.match.MatchScheduler;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.service.TotalMatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchSchedulerTest {
    @Mock
    TaskScheduler taskScheduler;
    @Mock
    TotalMatchService totalMatchService;
    @InjectMocks
    MatchScheduler matchScheduler;

    private Matches match(Long id) {
        Matches match = Matches.builder()
                .homeTeamId(10L).visitTeamId(20L)
                .matchStatus(MatchStatus.BEFORE)
                .scheduledStartedTime(LocalDateTime.now().plusHours(1))
                .seasonYear("2026")
                .gamePk(777)
                .build();
        ReflectionTestUtils.setField(match, "id", id);
        return match;
    }

    @Test
    @DisplayName("같은 경기를 두 번 등록해도 시작 스케쥴은 한 번만 등록된다 (신규 저장 + 서버 재시작 복구)")
    void scheduleMatchStartOnlyOnce() {
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        matchScheduler.scheduleMatchStart(match(1L));
        matchScheduler.scheduleMatchStart(match(1L));

        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("이미 폴링 중인 경기는 시작 스케쥴을 다시 등록하지 않는다")
    void skipScheduleWhenAlreadyPolling() {
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        matchScheduler.startPollingForUpdateMatch(1L, 777);

        matchScheduler.scheduleMatchStart(match(1L));

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("폴링을 교체하면 이전 폴링 작업은 취소된다")
    void replacingPollingCancelsPrevious() {
        ScheduledFuture<?> startPolling = mock(ScheduledFuture.class);
        ScheduledFuture<?> livePolling = mock(ScheduledFuture.class);
        doReturn(startPolling, livePolling).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        matchScheduler.startPollingForMatchStart(1L);
        matchScheduler.startPollingForUpdateMatch(1L, 777);

        verify(startPolling).cancel(false);
        verify(livePolling, never()).cancel(false);
    }

    @Test
    @DisplayName("폴링 종료 시 대기 중인 시작 스케쥴도 취소된다")
    void endedPollingCancelsPendingStart() {
        ScheduledFuture<?> pending = mock(ScheduledFuture.class);
        doReturn(pending).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        matchScheduler.scheduleMatchStart(match(1L));

        matchScheduler.endedPolling(1L);

        verify(pending).cancel(false);
    }
}
