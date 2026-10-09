package com.mlbbroadcast.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties("auth")
@Getter
@Setter
public class AuthProperties {
    private Duration registerTempTokenDuration;
}
