package com.mlbbroadcast.match.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//세션별 구독 목록 추적 (종료된 경기 구독자 연결 해제에 사용)
@Component
public class MatchSubscriptionTracker {

    public record Subscription(String sessionId, String subscriptionId) {}

    //sessionId -> (subscriptionId -> destination)
    private final Map<String, Map<String, String>> subscriptions = new ConcurrentHashMap<>();

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event){
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if(accessor.getSessionId() == null || accessor.getSubscriptionId() == null || accessor.getDestination() == null) return;
        subscriptions.computeIfAbsent(accessor.getSessionId(), id -> new ConcurrentHashMap<>())
                .put(accessor.getSubscriptionId(), accessor.getDestination());
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event){
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        remove(accessor.getSessionId(), accessor.getSubscriptionId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event){
        subscriptions.remove(event.getSessionId());
    }

    public List<Subscription> subscriptionsTo(String destination){
        return subscriptions.entrySet().stream()
                .flatMap(session -> session.getValue().entrySet().stream()
                        .filter(subscription -> destination.equals(subscription.getValue()))
                        .map(subscription -> new Subscription(session.getKey(), subscription.getKey())))
                .toList();
    }

    //해당 구독 외에 같은 세션의 다른 구독이 있는지 (다른 경기/채팅 등)
    public boolean hasOtherSubscriptions(Subscription subscription){
        Map<String, String> sessionSubscriptions = subscriptions.get(subscription.sessionId());
        return sessionSubscriptions != null && sessionSubscriptions.keySet().stream()
                .anyMatch(id -> !id.equals(subscription.subscriptionId()));
    }

    public void remove(String sessionId, String subscriptionId){
        if(sessionId == null || subscriptionId == null) return;
        subscriptions.computeIfPresent(sessionId, (id, sessionSubscriptions) -> {
            sessionSubscriptions.remove(subscriptionId);
            return sessionSubscriptions.isEmpty() ? null : sessionSubscriptions;
        });
    }
}
