package com.mlbbroadcast.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("oauth")
@Getter
@Setter
public class OauthProperties {
    private String googleOauthId;
    private String googleOauthSecret;
    private String googleRedirectUri;
}
