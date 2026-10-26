package org.example.mlbbroadcast.match;

import com.mlbbroadcast.match.polling.InMemoryJobLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryJobLockTest {

    @Test
    @DisplayName("같은 이름의 작업이 실행 중이면 다른 스레드의 실행은 건너뛴다")
    void skipWhileRunning() throws Exception {
        InMemoryJobLock lock = new InMemoryJobLock();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();

        Thread first = new Thread(() -> lock.runExclusively("sync", () -> {
            runs.incrementAndGet();
            running.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }));
        first.start();
        running.await(5, TimeUnit.SECONDS);

        AtomicBoolean secondRan = new AtomicBoolean(true);
        Thread second = new Thread(() -> secondRan.set(lock.runExclusively("sync", runs::incrementAndGet)));
        second.start();
        second.join();
        release.countDown();
        first.join();

        assertThat(secondRan).isFalse();
        assertThat(runs).hasValue(1);
        assertThat(lock.runExclusively("sync", runs::incrementAndGet)).isTrue();
    }
}
