package com.mlbbroadcast.bet;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties("bet")
@Getter
@Setter
public class BetProperties {
    private int minAmount;
    private int maxAmount;
    private int lockRetryMaxAttempts;
    private Duration lockRetryMinWait;
    private Duration lockRetryMaxWait;
    private Duration scanDelayAfterEnd;
    private Duration scanWindow;
    private int maxCorrectionAttempts;
}
