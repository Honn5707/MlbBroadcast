package org.example.mlbbroadcast.security;

import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.security.JwtAuthenticator;
import com.mlbbroadcast.security.StompAuthChannelInterceptor;
import com.mlbbroadcast.util.TokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {
    @Mock
    TokenProvider tokenProvider;

    StompAuthChannelInterceptor interceptor;
    final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(new JwtAuthenticator(tokenProvider));
    }

    private Message<byte[]> send(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> frame(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) accessor.addNativeHeader("Authorization", authorization);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("CONNECT에 유효한 토큰이 있으면 memberId를 principal로 등록한다")
    void connectWithValidToken() {
        when(tokenProvider.validateToken("valid")).thenReturn(1L);

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer valid"), channel);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNotNull();
        assertThat(StompHeaderAccessor.wrap(result).getUser().getName()).isEqualTo("1");
    }

    @Test
    @DisplayName("CONNECT에 토큰이 없으면 익명으로 연결을 허용한다 (비로그인 중계 시청)")
    void connectWithoutToken() {
        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, null), channel);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNull();
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 토큰 검증 없이 익명으로 연결한다")
    void connectWithoutBearerPrefix() {
        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "valid"), channel);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNull();
        verify(tokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("만료된 토큰이어도 연결은 막지 않고 익명으로 진행한다")
    void connectWithExpiredToken() {
        when(tokenProvider.validateToken("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer expired"), channel);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNull();
    }

    @Test
    @DisplayName("위조/형식 오류 토큰이어도 연결은 막지 않고 익명으로 진행한다")
    void connectWithInvalidToken() {
        when(tokenProvider.validateToken("broken")).thenThrow(new MalformedJwtException("broken"));

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer broken"), channel);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNull();
    }

    @Test
    @DisplayName("CONNECT 이외의 프레임은 토큰 검사 없이 통과한다 (세션에 등록된 사용자 사용)")
    void subscribePassesThrough() {
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, null);

        assertThat(interceptor.preSend(subscribe, channel)).isSameAs(subscribe);
        verify(tokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("JwtAuthenticator: subject가 숫자가 아니면 INVALID_TOKEN")
    void nonNumericSubject() {
        when(tokenProvider.validateToken("weird")).thenThrow(new NumberFormatException("abc"));

        assertThatThrownBy(() -> new JwtAuthenticator(tokenProvider).authenticate("weird"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("클라이언트가 /topic으로 직접 SEND하면 거부한다 (중계 데이터 위조 방지)")
    void rejectSendToBroker() {
        assertThatThrownBy(() -> interceptor.preSend(send("/topic/games/1/current-plays"), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining(ErrorCode.ACCESS_DENIED.name());
    }

    @Test
    @DisplayName("/app 목적지로의 SEND는 허용한다")
    void allowSendToApp() {
        Message<byte[]> message = send("/app/chat");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("/topic으로 시작하지만 다른 경로(/topicx)는 브로커 목적지로 보지 않는다")
    void similarPrefixIsNotBroker() {
        Message<byte[]> message = send("/topicx/1");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }
}
