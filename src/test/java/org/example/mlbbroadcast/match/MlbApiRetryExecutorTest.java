package org.example.mlbbroadcast.match;

import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.external.mlbStatus.retry.MlbApiRetryExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MlbApiRetryExecutorTest {
    @Mock TaskScheduler taskScheduler;
    static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");
    MlbApiRetryExecutor executor;

    @BeforeEach
    void setUp() {
        DefaultProperties properties = new DefaultProperties();
        properties.getMlbApiRetry().setInitialDelay(Duration.ofSeconds(5));
        properties.getMlbApiRetry().setMaxDelay(Duration.ofSeconds(60));
        properties.getMlbApiRetry().setMaxAttempts(6);
        executor = new MlbApiRetryExecutor(taskScheduler, properties, new MutableClock(NOW));
    }

    //예약된 재시도를 즉시 실행하고, 예약 시점(지연)을 기록
    private List<Duration> runScheduledRetries() {
        List<Duration> delays = new ArrayList<>();
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            delays.add(Duration.between(NOW, invocation.<Instant>getArgument(1)));
            invocation.<Runnable>getArgument(0).run();
            return null;
        });
        return delays;
    }

    @Test
    @DisplayName("성공하면 재시도를 예약하지 않는다")
    void success() {
        AtomicInteger calls = new AtomicInteger();

        executor.execute("job", calls::incrementAndGet);

        assertThat(calls).hasValue(1);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("[10-9] 5xx는 5s -> 10s -> 20s -> 40s -> 60s(상한) 간격으로 다음 실행을 예약하고, 한도에서 멈춘다")
    void serverErrorBackoffUntilLimit() {
        List<Duration> delays = runScheduledRetries();
        AtomicInteger calls = new AtomicInteger();

        executor.execute("job", () -> { calls.incrementAndGet(); throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY); });

        assertThat(calls).hasValue(6);
        assertThat(delays).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(20),
                Duration.ofSeconds(40), Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("타임아웃(ResourceAccessException)은 재시도하고, 성공하면 멈춘다")
    void timeoutThenSuccess() {
        List<Duration> delays = runScheduledRetries();
        AtomicInteger calls = new AtomicInteger();

        executor.execute("job", () -> { if (calls.incrementAndGet() < 3) throw new ResourceAccessException("timeout"); });

        assertThat(calls).hasValue(3);
        assertThat(delays).hasSize(2);
    }

    @Test
    @DisplayName("429는 Retry-After가 백오프보다 길면 그 값을 따른다")
    void tooManyRequestsRespectsRetryAfter() {
        List<Duration> delays = runScheduledRetries();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "30");
        AtomicInteger calls = new AtomicInteger();

        executor.execute("job", () -> {
            if (calls.incrementAndGet() == 1)
                throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", headers, new byte[0], null);
        });

        assertThat(delays).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("[10-10] 4xx(429 제외)는 즉시 실패하고 재시도하지 않는다")
    void clientErrorFailsImmediately() {
        AtomicInteger calls = new AtomicInteger();

        executor.execute("job", () -> { calls.incrementAndGet(); throw new HttpClientErrorException(HttpStatus.NOT_FOUND); });

        assertThat(calls).hasValue(1);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("외부 API 외의 예외(DB 오류, NPE 등)는 재시도하지 않는다")
    void otherExceptionsNotRetried() {
        executor.execute("job", () -> { throw new IllegalStateException("bug"); });

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(MlbApiRetryExecutor.isRetryable(new RuntimeException(new ResourceAccessException("wrapped")))).isTrue();
    }
}
