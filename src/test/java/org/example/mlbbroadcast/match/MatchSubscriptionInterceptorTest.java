package org.example.mlbbroadcast.match;

import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.match.MatchSubscriptionInterceptor;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchSubscriptionInterceptorTest {
    @Mock MatchesRepository matchesRepository;
    MatchSubscriptionInterceptor interceptor;
    final MessageChannel channel = mock(MessageChannel.class);
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 3, 0);

    @BeforeEach
    void setUp() {
        DefaultProperties properties = new DefaultProperties();
        properties.getPlayInfo().setEndedMatchDisconnectDelay(Duration.ofMinutes(10));
        interceptor = new MatchSubscriptionInterceptor(matchesRepository, properties, new MutableClock(Instant.parse("2026-10-09T03:00:00Z")));
    }

    private Message<byte[]> subscribe(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void match(long id, MatchStatus status, LocalDateTime endedTime) {
        Matches match = Matches.builder().matchStatus(status).scheduledStartedTime(NOW.minusHours(4)).build();
        match.matchEndTimeSet(endedTime);
        when(matchesRepository.findById(id)).thenReturn(Optional.of(match));
    }

    @Test
    @DisplayName("진행 중인 경기 구독은 허용한다")
    void live() {
        match(1L, MatchStatus.LIVE, null);
        Message<byte[]> message = subscribe("/topic/matches/1");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("존재하지 않는 matchId 구독은 거절한다")
    void notExisting() {
        when(matchesRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/matches/99"), channel))
                .isInstanceOf(MessageDeliveryException.class).hasMessageContaining("MATCH_NOT_FOUND");
    }

    @Test
    @DisplayName("FINAL 후 10분 이내는 구독 허용, 10분이 지나면 MATCH_ENDED로 거절한다")
    void finalWithinAndAfterWindow() {
        match(1L, MatchStatus.FINAL, NOW.minusMinutes(5));
        match(2L, MatchStatus.FINAL, NOW.minusMinutes(11));

        assertThat(interceptor.preSend(subscribe("/topic/matches/1"), channel)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/matches/2"), channel))
                .isInstanceOf(MessageDeliveryException.class).hasMessageContaining("MATCH_ENDED");
    }

    @Test
    @DisplayName("취소된 경기는 라이브 데이터가 없으므로 구독을 거절한다")
    void canceled() {
        match(1L, MatchStatus.CANCELED, null);

        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/matches/1"), channel))
                .isInstanceOf(MessageDeliveryException.class).hasMessageContaining("MATCH_ENDED");
    }

    @Test
    @DisplayName("숫자가 아닌 matchId는 DB 조회 없이 거절, 경기 중계가 아닌 목적지는 검사하지 않는다")
    void nonNumericAndOtherDestination() {
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/matches/abc"), channel))
                .isInstanceOf(MessageDeliveryException.class);
        Message<byte[]> chat = subscribe("/topic/chat/1");
        assertThat(interceptor.preSend(chat, channel)).isSameAs(chat);
        verify(matchesRepository, never()).findById(anyLong());
    }
}
