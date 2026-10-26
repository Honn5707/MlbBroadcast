package com.mlbbroadcast.configuration;

import com.mlbbroadcast.match.MatchSubscriptionInterceptor;
import com.mlbbroadcast.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import com.mlbbroadcast.websocket.WebSocketSessionRegistry;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final MatchSubscriptionInterceptor matchSubscriptionInterceptor;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");

    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry){
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");

    }

    //서버에서 연결을 끊을 수 있도록 세션 보관 (종료된 경기 구독 해제)
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration){
        registration.addDecoratorFactory(webSocketSessionRegistry::decorate);
    }

    //클라이언트 -> 서버 메시지에 토큰 인증(CONNECT), 목적지 제한(SEND), 경기 존재 확인(SUBSCRIBE) 적용
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration){
        registration.interceptors(stompAuthChannelInterceptor, matchSubscriptionInterceptor);
    }
}
