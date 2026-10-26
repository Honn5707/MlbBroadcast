package com.mlbbroadcast.external.mlbStatus.retry;

import com.mlbbroadcast.configuration.DefaultProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

//외부 API 재시도 (docs/design/MatchSystem 6장). 현재 타석 조회/status 폴링은 폴링 자체가 재시도이므로 사용하지 않음
//스레드를 붙잡고 대기하지 않고 TaskScheduler에 다음 실행을 예약하는 방식 (스케쥴러 스레드가 다른 경기 폴링을 막지 않도록)
//실패 상태는 서버 로그로 기록
@Slf4j
@Component
@RequiredArgsConstructor
public class MlbApiRetryExecutor {
    private final TaskScheduler taskScheduler;
    private final DefaultProperties properties;
    private final Clock clock;

    //첫 시도는 호출한 스레드에서 바로 실행, 재시도 대상 실패면 다음 실행 예약
    public void execute(String jobName, Runnable task){
        attempt(jobName, task, 1);
    }

    private void attempt(String jobName, Runnable task, int attempt){
        try {
            task.run();
            if(attempt > 1) log.info("외부 API 재시도 성공: job={}, attempt={}", jobName, attempt);
        } catch (Exception e) {
            if(!isRetryable(e)){
                log.error("외부 API 실패(재시도 대상 아님): job={}, attempt={}", jobName, attempt, e);
                return;
            }
            DefaultProperties.MlbApiRetry retry = properties.getMlbApiRetry();
            if(attempt >= retry.getMaxAttempts()){
                log.error("외부 API 재시도 한도 초과: job={}, attempts={} - 다음 정기 배치 또는 수동 재처리 필요", jobName, attempt, e);
                return;
            }
            Duration delay = nextDelay(e, attempt);
            log.warn("외부 API 일시 실패, {} 후 재시도 예약: job={}, attempt={}/{}, cause={}",
                    delay, jobName, attempt, retry.getMaxAttempts(), e.toString());
            taskScheduler.schedule(() -> attempt(jobName, task, attempt + 1), clock.instant().plus(delay));
        }
    }

    //재시도 대상: 타임아웃/연결 오류, 5xx, 429. 나머지 4xx와 그 외 예외는 즉시 실패
    public static boolean isRetryable(Throwable e){
        for(Throwable t = e; t != null; t = t.getCause()){
            if(t instanceof ResourceAccessException) return true;
            if(t instanceof HttpServerErrorException) return true;
            if(t instanceof RestClientResponseException response)
                return response.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value();
            if(t.getCause() == t) break;
        }
        return false;
    }

    //5s -> 10s -> 20s ... 상한 60s. 429는 Retry-After(초)가 더 길면 그 값을 따름
    Duration nextDelay(Throwable e, int attempt){
        DefaultProperties.MlbApiRetry retry = properties.getMlbApiRetry();
        Duration backoff = retry.getInitialDelay().multipliedBy(1L << Math.min(attempt - 1, 20));
        if(backoff.compareTo(retry.getMaxDelay()) > 0) backoff = retry.getMaxDelay();
        Duration finalBackoff = backoff;
        return retryAfter(e).filter(retryAfter -> retryAfter.compareTo(finalBackoff) > 0).orElse(backoff);
    }

    private Optional<Duration> retryAfter(Throwable e){
        for(Throwable t = e; t != null; t = t.getCause()){
            if(t instanceof RestClientResponseException response && response.getResponseHeaders() != null){
                String value = response.getResponseHeaders().getFirst("Retry-After");
                if(value == null) return Optional.empty();
                try {
                    return Optional.of(Duration.ofSeconds(Long.parseLong(value.trim())));
                } catch (NumberFormatException ignored) {
                    //HTTP-date 형식은 지원하지 않고 기본 백오프 사용
                    return Optional.empty();
                }
            }
            if(t.getCause() == t) break;
        }
        return Optional.empty();
    }
}
