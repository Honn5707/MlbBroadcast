package com.mlbbroadcast.security;

import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

//STOMP CONNECT 프레임의 Authorization 헤더로 인증. 브라우저 WebSocket은 핸드셰이크에 헤더를 넣을 수 없어 CONNECT 단계에서 검증
//CONNECT 시 등록한 사용자는 세션에 유지되어 이후 SUBSCRIBE/SEND 메시지에도 적용됨
//HTTP 필터와 같은 정책: 토큰이 없거나 무효여도 연결은 허용(익명), 유효하면 사용자 등록 (docs/design/AuthSystem.md 참고)
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    //서버만 발행하는 브로커 목적지 (WebSocketConfiguration.enableSimpleBroker 참고)
    private static final String BROKER_PREFIX = "/topic";

    private final JwtAuthenticator jwtAuthenticator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(accessor == null) return message;

        if(StompCommand.CONNECT.equals(accessor.getCommand())) authenticate(accessor);
        else if(StompCommand.SEND.equals(accessor.getCommand())) rejectBrokerSend(accessor);
        return message;
    }

    //클라이언트가 /topic으로 직접 SEND하면 심플 브로커가 그대로 구독자 전원에게 전달하므로 차단 (중계 데이터 위조 방지)
    //클라이언트 발행은 /app 목적지(@MessageMapping)로만 허용
    private void rejectBrokerSend(StompHeaderAccessor accessor){
        String destination = accessor.getDestination();
        if(destination != null && (destination.equals(BROKER_PREFIX) || destination.startsWith(BROKER_PREFIX + "/"))){
            throw new MessageDeliveryException(ErrorCode.ACCESS_DENIED.name());
        }
    }

    private void authenticate(StompHeaderAccessor accessor){

        String token = JwtAuthenticator.resolveToken(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION));
        //비로그인 사용자도 중계를 볼 수 있도록 익명 연결 허용
        if(token == null) return;

        try {
            accessor.setUser(jwtAuthenticator.authenticate(token));
        } catch (BusinessException e) {
            //만료/위조 토큰이어도 연결은 막지 않고 익명으로 진행. 보호할 목적지가 생기면 해당 목적지에서 거부
            log.debug("STOMP CONNECT 토큰 검증 실패, 익명으로 연결: {}", e.getErrorCode());
        }
    }
}
