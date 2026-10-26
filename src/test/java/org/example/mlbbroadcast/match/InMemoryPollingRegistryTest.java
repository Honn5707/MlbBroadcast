package org.example.mlbbroadcast.match;

import com.mlbbroadcast.match.polling.InMemoryPollingRegistry;
import com.mlbbroadcast.match.polling.PollingPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InMemoryPollingRegistryTest {
    final InMemoryPollingRegistry registry = new InMemoryPollingRegistry();

    @Test
    @DisplayName("[10-2] 같은 gamePk를 두 번 등록하면 두 번째는 거부되고 작업도 시작되지 않는다")
    void rejectDuplicate() {
        AtomicInteger started = new AtomicInteger();

        assertThat(registry.register(777, PollingPhase.STATUS, () -> { started.incrementAndGet(); return mock(ScheduledFuture.class); })).isTrue();
        assertThat(registry.register(777, PollingPhase.WAITING_START, () -> { started.incrementAndGet(); return mock(ScheduledFuture.class); })).isFalse();

        assertThat(started).hasValue(1);
        assertThat(registry.phaseOf(777)).contains(PollingPhase.STATUS);
    }

    @Test
    @DisplayName("단계 전환(replace) 시 이전 작업을 취소하고 새 작업으로 교체한다")
    void replace() {
        ScheduledFuture<?> status = mock(ScheduledFuture.class);
        ScheduledFuture<?> live = mock(ScheduledFuture.class);
        registry.register(777, PollingPhase.STATUS, () -> status);

        registry.replace(777, PollingPhase.LIVE_PLAY, () -> live);

        verify(status).cancel(false);
        verify(live, never()).cancel(false);
        assertThat(registry.phaseOf(777)).contains(PollingPhase.LIVE_PLAY);
    }

    @Test
    @DisplayName("취소하면 작업이 취소되고 다시 등록할 수 있다")
    void cancelThenRegister() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        registry.register(777, PollingPhase.STATUS, () -> future);

        registry.cancel(777);

        verify(future).cancel(false);
        assertThat(registry.phaseOf(777)).isEmpty();
        assertThat(registry.register(777, PollingPhase.STATUS, () -> mock(ScheduledFuture.class))).isTrue();
    }

    @Test
    @DisplayName("다른 gamePk는 서로 영향을 주지 않는다")
    void independentKeys() {
        assertThat(registry.register(1, PollingPhase.STATUS, () -> mock(ScheduledFuture.class))).isTrue();
        assertThat(registry.register(2, PollingPhase.STATUS, () -> mock(ScheduledFuture.class))).isTrue();
    }
}
