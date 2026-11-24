package com.mlbbroadcast.bet;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

//락 획득 실패 후 재시도 전 대기 (테스트에서 대기 없이 교체할 수 있도록 분리)
@FunctionalInterface
public interface BetRetryWaiter {
    void waitBeforeRetry() throws InterruptedException;

    //min ~ max 사이 난수 시간 대기 (여러 정산이 같은 회원 락을 동시에 다시 노리지 않도록)
    static BetRetryWaiter randomBetween(Duration min, Duration max){
        return () -> Thread.sleep(ThreadLocalRandom.current().nextLong(min.toMillis(), max.toMillis() + 1));
    }
}
