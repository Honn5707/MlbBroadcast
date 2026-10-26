package com.mlbbroadcast.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//서버에서 특정 웹소켓 연결을 끊기 위해 세션을 보관 (STOMP simpSessionId == WebSocketSession id)
@Slf4j
@Component
public class WebSocketSessionRegistry {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    //WebSocketConfiguration.configureWebSocketTransport에 등록
    public WebSocketHandler decorate(WebSocketHandler handler){
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    public void close(String sessionId, CloseStatus status){
        WebSocketSession session = sessions.get(sessionId);
        if(session == null || !session.isOpen()) return;
        try {
            session.close(status);
        } catch (IOException e) {
            log.warn("웹소켓 연결 종료 실패: sessionId={}", sessionId, e);
        }
    }
}
