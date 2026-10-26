package com.mlbbroadcast.match.polling;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;

//gamePk당 폴링 작업은 하나만 존재하도록 관리 (단일 인스턴스 전제, 다중화 시 분산 락 구현체로 교체)
public interface PollingRegistry {

    //이미 등록된 gamePk면 등록을 거부하고 false. taskStarter는 등록이 허용될 때만 실행됨
    boolean register(int gamePk, PollingPhase phase, Supplier<ScheduledFuture<?>> taskStarter);

    //같은 경기의 폴링 단계 전환 (기존 작업 취소 후 새 작업 등록)
    void replace(int gamePk, PollingPhase phase, Supplier<ScheduledFuture<?>> taskStarter);

    void cancel(int gamePk);

    Optional<PollingPhase> phaseOf(int gamePk);
}
