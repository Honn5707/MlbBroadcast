package com.mlbbroadcast.match.polling;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;

//메모리 기반 구현. 확인-등록-제거를 JVM 락(synchronized)으로 묶어 등록/제거 레이스를 방지
@Component
public class InMemoryPollingRegistry implements PollingRegistry {

    private record Entry(PollingPhase phase, ScheduledFuture<?> future) {}

    private final Map<Integer, Entry> entries = new HashMap<>();

    @Override
    public synchronized boolean register(int gamePk, PollingPhase phase, Supplier<ScheduledFuture<?>> taskStarter) {
        if(entries.containsKey(gamePk)) return false;
        entries.put(gamePk, new Entry(phase, taskStarter.get()));
        return true;
    }

    @Override
    public synchronized void replace(int gamePk, PollingPhase phase, Supplier<ScheduledFuture<?>> taskStarter) {
        cancelFuture(entries.remove(gamePk));
        entries.put(gamePk, new Entry(phase, taskStarter.get()));
    }

    @Override
    public synchronized void cancel(int gamePk) {
        cancelFuture(entries.remove(gamePk));
    }

    @Override
    public synchronized Optional<PollingPhase> phaseOf(int gamePk) {
        return Optional.ofNullable(entries.get(gamePk)).map(Entry::phase);
    }

    //실행 중인 작업은 끝까지 수행하도록 interrupt 하지 않음
    private void cancelFuture(Entry entry){
        if(entry != null && entry.future() != null) entry.future().cancel(false);
    }
}
