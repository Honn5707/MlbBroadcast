package com.mlbbroadcast.match.websocket;

import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.match.event.MatchCanceledEvent;
import com.mlbbroadcast.match.event.MatchFinalizedEvent;
import com.mlbbroadcast.websocket.WebSocketSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.time.Clock;
import java.time.Duration;

//종료된 경기(FINAL/CANCELED)는 일정 시간(기본 10분) 뒤 해당 경기 구독을 해제
//해당 경기만 구독 중인 연결은 웹소켓 연결을 끊고, 다른 구독(다른 경기/채팅)도 있는 연결은 이 경기 구독만 브로커에서 제거
@Slf4j
@Component
public class EndedMatchDisconnector {
    public static final CloseStatus MATCH_ENDED = new CloseStatus(4000, "MATCH_ENDED");
    private static final String MATCH_TOPIC_PREFIX = "/topic/matches/";

    private final TaskScheduler taskScheduler;
    private final MatchSubscriptionTracker subscriptionTracker;
    private final WebSocketSessionRegistry sessionRegistry;
    private final MessageChannel brokerChannel;
    private final DefaultProperties properties;
    private final Clock clock;

    public EndedMatchDisconnector(TaskScheduler taskScheduler, MatchSubscriptionTracker subscriptionTracker,
                                  WebSocketSessionRegistry sessionRegistry, @Qualifier("brokerChannel") MessageChannel brokerChannel,
                                  DefaultProperties properties, Clock clock) {
        this.taskScheduler = taskScheduler;
        this.subscriptionTracker = subscriptionTracker;
        this.sessionRegistry = sessionRegistry;
        this.brokerChannel = brokerChannel;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener
    public void onFinalized(MatchFinalizedEvent event){
        scheduleDisconnect(event.matchId());
    }

    @EventListener
    public void onCanceled(MatchCanceledEvent event){
        scheduleDisconnect(event.matchId());
    }

    //예약은 메모리에만 있으므로 서버 재시작 시 유실됨 (재시작 후에는 신규 구독만 MatchSubscriptionInterceptor가 거절)
    private void scheduleDisconnect(Long matchId){
        Duration delay = properties.getPlayInfo().getEndedMatchDisconnectDelay();
        taskScheduler.schedule(() -> disconnect(matchId), clock.instant().plus(delay));
        log.info("종료된 경기 구독 해제 예약: matchId={}, {} 후", matchId, delay);
    }

    public void disconnect(Long matchId){
        String destination = MATCH_TOPIC_PREFIX + matchId;
        int closed = 0, unsubscribed = 0;
        for(MatchSubscriptionTracker.Subscription subscription : subscriptionTracker.subscriptionsTo(destination)){
            if(subscriptionTracker.hasOtherSubscriptions(subscription)){
                unsubscribeFromBroker(subscription);
                unsubscribed++;
            } else {
                sessionRegistry.close(subscription.sessionId(), MATCH_ENDED);
                closed++;
            }
            subscriptionTracker.remove(subscription.sessionId(), subscription.subscriptionId());
        }
        log.info("종료된 경기 구독 해제: matchId={}, 연결 종료={}, 구독만 해제={}", matchId, closed, unsubscribed);
    }

    //서버 측에서 브로커의 구독만 제거 (클라이언트에는 별도 프레임이 가지 않으며, 이후 이 경기 메시지를 받지 않음)
    private void unsubscribeFromBroker(MatchSubscriptionTracker.Subscription subscription){
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        accessor.setSessionId(subscription.sessionId());
        accessor.setSubscriptionId(subscription.subscriptionId());
        accessor.setLeaveMutable(true);
        brokerChannel.send(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
    }
}
