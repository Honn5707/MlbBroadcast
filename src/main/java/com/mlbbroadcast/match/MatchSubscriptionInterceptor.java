package com.mlbbroadcast.match;

import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

//실시간 중계 구독(/topic/matches/{matchId}) 검사 (docs/design/MatchSystem 8-2)
//- 존재하지 않는 경기: 거절
//- 종료된 경기: FINAL은 종료 후 구독 해제 시간(기본 10분)이 지나면 거절, CANCELED는 라이브 데이터가 없으므로 거절
@Component
@RequiredArgsConstructor
public class MatchSubscriptionInterceptor implements ChannelInterceptor {
    private static final String MATCH_TOPIC_PREFIX = "/topic/matches/";

    private final MatchesRepository matchesRepository;
    private final DefaultProperties properties;
    private final Clock clock;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) return message;

        String destination = accessor.getDestination();
        if(destination == null || !destination.startsWith(MATCH_TOPIC_PREFIX)) return message;

        Long matchId = parseMatchId(destination.substring(MATCH_TOPIC_PREFIX.length()));
        Matches match = matchId == null ? null : matchesRepository.findById(matchId).orElse(null);
        if(match == null) throw new MessageDeliveryException(ErrorCode.MATCH_NOT_FOUND.name());
        if(isClosedForSubscription(match)) throw new MessageDeliveryException(ErrorCode.MATCH_ENDED.name());
        return message;
    }

    private boolean isClosedForSubscription(Matches match){
        if(match.getMatchStatus() == MatchStatus.CANCELED) return true;
        if(match.getMatchStatus() != MatchStatus.FINAL || match.getEndedTime() == null) return false;
        LocalDateTime closeAt = match.getEndedTime().plus(properties.getPlayInfo().getEndedMatchDisconnectDelay());
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC)).isAfter(closeAt);
    }

    private Long parseMatchId(String value){
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
