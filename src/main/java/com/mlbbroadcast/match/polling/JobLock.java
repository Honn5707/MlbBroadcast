package com.mlbbroadcast.match.polling;

//배치/동기화 작업 중복 실행 방지 (서버 구동 실행과 cron 실행이 겹치는 경우 등)
//단일 인스턴스 전제, 다중화 시 분산 락(ShedLock 등) 구현체로 교체
public interface JobLock {

    //같은 이름의 작업이 실행 중이면 실행하지 않고 false
    boolean runExclusively(String jobName, Runnable job);
}
