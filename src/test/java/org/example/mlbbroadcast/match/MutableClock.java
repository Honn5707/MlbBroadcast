package org.example.mlbbroadcast.match;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

//시간 의존 로직(지연 한도, 무갱신 판단) 테스트용 시계
public class MutableClock extends Clock {
    private Instant now;

    public MutableClock(Instant now) { this.now = now; }

    public void advance(Duration duration) { now = now.plus(duration); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
}
