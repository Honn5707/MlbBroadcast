package com.mlbbroadcast.match.polling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class InMemoryJobLock implements JobLock {
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public boolean runExclusively(String jobName, Runnable job) {
        ReentrantLock lock = locks.computeIfAbsent(jobName, name -> new ReentrantLock());
        if(!lock.tryLock()){
            log.warn("이미 실행 중인 작업이라 건너뜀: {}", jobName);
            return false;
        }
        try {
            job.run();
            return true;
        } finally {
            lock.unlock();
        }
    }
}
