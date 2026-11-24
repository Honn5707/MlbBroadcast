package org.example.mlbbroadcast.bet;

import com.mlbbroadcast.bet.Bet;
import com.mlbbroadcast.bet.BetProperties;
import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.member.Members;
import com.mlbbroadcast.member.Provider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

final class BetTestFixtures {
    private BetTestFixtures() {}

    static BetProperties properties() {
        BetProperties properties = new BetProperties();
        properties.setMinAmount(100);
        properties.setMaxAmount(10_000);
        properties.setLockRetryMaxAttempts(5);
        properties.setLockRetryMinWait(Duration.ofMillis(500));
        properties.setLockRetryMaxWait(Duration.ofMillis(1000));
        properties.setScanDelayAfterEnd(Duration.ofMinutes(5));
        properties.setScanWindow(Duration.ofDays(2));
        properties.setMaxCorrectionAttempts(4);
        return properties;
    }

    static Members member(long id, int coin) {
        Members member = Members.builder().providerId("p" + id).providerType(Provider.GOOGLE).nickname("n" + id).createdAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "coinRemain", coin);
        return member;
    }

    static Matches match(long id, MatchStatus status, LocalDateTime scheduledStart) {
        Matches match = Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(status)
                .scheduledStartedTime(scheduledStart).seasonYear("2026").gamePk(777).build();
        ReflectionTestUtils.setField(match, "id", id);
        return match;
    }

    static Bet bet(long id, long matchId, long memberId, int amount, Side side) {
        Bet bet = Bet.builder().matchId(matchId).memberId(memberId).amount(amount).betSide(side).createdAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(bet, "id", id);
        return bet;
    }
}
