package com.mlbbroadcast.bet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BetConfiguration {

    //정산/보정 전용 스레드. 경기 상태 폴링 스레드(TaskScheduler)에서 정산(락 대기·재시도 대기)을 돌리면 폴링이 밀리므로 분리
    @Bean
    public TaskExecutor betTaskExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("bet-");
        executor.initialize();
        return executor;
    }

    @Bean
    public BetRetryWaiter betRetryWaiter(BetProperties properties){
        return BetRetryWaiter.randomBetween(properties.getLockRetryMinWait(), properties.getLockRetryMaxWait());
    }
}
