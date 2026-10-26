package org.example.mlbbroadcast.match;

import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.match.event.MatchCanceledEvent;
import com.mlbbroadcast.match.event.MatchFinalizedEvent;
import com.mlbbroadcast.match.websocket.EndedMatchDisconnector;
import com.mlbbroadcast.match.websocket.MatchSubscriptionTracker;
import com.mlbbroadcast.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.CloseStatus;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndedMatchDisconnectorTest {
    @Mock TaskScheduler taskScheduler;
    @Mock WebSocketSessionRegistry sessionRegistry;
    @Mock MessageChannel brokerChannel;

    MatchSubscriptionTracker tracker = new MatchSubscriptionTracker();
    EndedMatchDisconnector disconnector;
    static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");

    @BeforeEach
    void setUp() {
        DefaultProperties properties = new DefaultProperties();
        properties.getPlayInfo().setEndedMatchDisconnectDelay(Duration.ofMinutes(10));
        disconnector = new EndedMatchDisconnector(taskScheduler, tracker, sessionRegistry, brokerChannel, properties, new MutableClock(NOW));
    }

    private void subscribe(String sessionId, String subscriptionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);
        tracker.onSubscribe(new SessionSubscribeEvent(this, MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders())));
    }

    @Test
    @DisplayName("FINAL/CANCELED 이벤트를 받으면 10분 뒤 구독 해제를 예약한다")
    void scheduleAfter10Minutes() {
        disconnector.onFinalized(new MatchFinalizedEvent(1L));
        disconnector.onCanceled(new MatchCanceledEvent(2L));

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofMinutes(10))));
    }

    @Test
    @DisplayName("해당 경기만 구독 중인 연결은 웹소켓 연결을 끊는다 (close code 4000 MATCH_ENDED)")
    void closeSingleSubscriptionSession() {
        subscribe("s1", "sub-0", "/topic/matches/1");

        disconnector.disconnect(1L);

        verify(sessionRegistry).close("s1", EndedMatchDisconnector.MATCH_ENDED);
        assertThat(EndedMatchDisconnector.MATCH_ENDED.getCode()).isEqualTo(4000);
        verify(brokerChannel, never()).send(any());
        assertThat(tracker.subscriptionsTo("/topic/matches/1")).isEmpty();
    }

    @Test
    @DisplayName("다른 구독도 있는 연결은 끊지 않고 이 경기 구독만 브로커에서 제거한다")
    void unsubscribeOnlyWhenOtherSubscriptions() {
        subscribe("s2", "sub-0", "/topic/matches/1");
        subscribe("s2", "sub-1", "/topic/matches/2");

        disconnector.disconnect(1L);

        verify(sessionRegistry, never()).close(any(), any(CloseStatus.class));
        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        verify(brokerChannel).send(captor.capture());
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(captor.getValue());
        assertThat(headers.getMessageType()).isEqualTo(SimpMessageType.UNSUBSCRIBE);
        assertThat(headers.getSessionId()).isEqualTo("s2");
        assertThat(headers.getSubscriptionId()).isEqualTo("sub-0");
        assertThat(tracker.subscriptionsTo("/topic/matches/2")).hasSize(1);
    }

    @Test
    @DisplayName("다른 경기 구독자나 이미 연결이 끊긴 세션은 건드리지 않는다")
    void otherMatchesUntouched() {
        subscribe("s3", "sub-0", "/topic/matches/2");
        subscribe("s4", "sub-0", "/topic/matches/1");
        tracker.onDisconnect(new SessionDisconnectEvent(this, MessageBuilder.createMessage(new byte[0], StompHeaderAccessor.create(StompCommand.DISCONNECT).getMessageHeaders()), "s4", CloseStatus.NORMAL));

        disconnector.disconnect(1L);

        verifyNoInteractions(sessionRegistry, brokerChannel);
    }
}
