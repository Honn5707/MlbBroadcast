package org.example.mlbbroadcast.bet;

import com.mlbbroadcast.bet.Bet;
import com.mlbbroadcast.bet.BetRepository;
import com.mlbbroadcast.bet.BetService;
import com.mlbbroadcast.bet.dto.BetResponse;
import com.mlbbroadcast.bet.dto.PlaceBetRequest;
import com.mlbbroadcast.coin.CoinTransactionService;
import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.member.Members;
import com.mlbbroadcast.member.MembersRepository;
import org.example.mlbbroadcast.match.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BetServiceTest {
    @Mock BetRepository betRepository;
    @Mock MatchesRepository matchesRepository;
    @Mock MembersRepository membersRepository;
    @Mock CoinTransactionService coinTransactionService;
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    BetService service;

    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    static final LocalDateTime START = LocalDateTime.of(2026, 11, 20, 12, 0);

    @BeforeEach
    void setUp() {
        service = new BetService(betRepository, matchesRepository, membersRepository, coinTransactionService,
                BetTestFixtures.properties(), new TransactionTemplate(txManager), new MutableClock(NOW));
        when(matchesRepository.findById(1L)).thenReturn(Optional.of(BetTestFixtures.match(1L, MatchStatus.SCHEDULED, START)));
        when(betRepository.saveAndFlush(any(Bet.class))).thenAnswer(invocation -> {
            Bet bet = invocation.getArgument(0);
            ReflectionTestUtils.setField(bet, "id", 99L);
            return bet;
        });
    }

    private PlaceBetRequest request(int amount) {
        return new PlaceBetRequest(1L, Side.HOME, amount);
    }

    private void errorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(expected);
    }

    @Test
    @DisplayName("정상 베팅: 회원 락 -> 잔액 확인 -> Bet 생성(PENDING) -> 코인 차감 순서, 하나의 트랜잭션")
    void placeBet() {
        Members member = BetTestFixtures.member(7L, 5_000);
        when(membersRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        BetResponse response = service.placeBet(7L, request(1_000));

        assertThat(response.betId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(com.mlbbroadcast.bet.BetStatus.PENDING);
        InOrder order = inOrder(txManager, membersRepository, betRepository, coinTransactionService);
        order.verify(txManager).getTransaction(any());
        order.verify(membersRepository).findByIdForUpdate(7L);
        order.verify(betRepository).saveAndFlush(any(Bet.class));
        order.verify(coinTransactionService).spendForBet(member, 99L, 1_000);
        order.verify(txManager).commit(any());
    }

    @Test
    @DisplayName("베팅 금액은 100 ~ 10,000 (범위 밖은 트랜잭션 시작 전 거절)")
    void amountRange() {
        errorCode(() -> service.placeBet(7L, request(99)), ErrorCode.BET_INVALID_AMOUNT);
        errorCode(() -> service.placeBet(7L, request(10_001)), ErrorCode.BET_INVALID_AMOUNT);
        verifyNoInteractions(txManager);
    }

    @Test
    @DisplayName("NONE에는 베팅할 수 없다")
    void invalidSide() {
        errorCode(() -> service.placeBet(7L, new PlaceBetRequest(1L, Side.NONE, 1_000)), ErrorCode.BET_INVALID_SIDE);
    }

    @Test
    @DisplayName("SCHEDULED가 아니거나, SCHEDULED라도 시작 시각이 지났으면 마감")
    void closed() {
        when(matchesRepository.findById(2L)).thenReturn(Optional.of(BetTestFixtures.match(2L, MatchStatus.LIVE, START)));
        when(matchesRepository.findById(3L)).thenReturn(Optional.of(BetTestFixtures.match(3L, MatchStatus.SCHEDULED, LocalDateTime.of(2026, 11, 20, 9, 59))));

        errorCode(() -> service.placeBet(7L, new PlaceBetRequest(2L, Side.HOME, 1_000)), ErrorCode.BET_CLOSED);
        errorCode(() -> service.placeBet(7L, new PlaceBetRequest(3L, Side.HOME, 1_000)), ErrorCode.BET_CLOSED);
        verifyNoInteractions(txManager);
    }

    @Test
    @DisplayName("이미 베팅한 경기는 트랜잭션 전에 거절")
    void duplicate() {
        when(betRepository.existsByMemberIdAndMatchId(7L, 1L)).thenReturn(true);

        errorCode(() -> service.placeBet(7L, request(1_000)), ErrorCode.BET_ALREADY_PLACED);
        verifyNoInteractions(txManager);
    }

    @Test
    @DisplayName("동시 요청으로 UNIQUE(member_id, match_id) 위반 시 중복 베팅(409), 코인 차감 없음")
    void duplicateRace() {
        when(membersRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(BetTestFixtures.member(7L, 5_000)));
        when(betRepository.saveAndFlush(any(Bet.class))).thenThrow(new DataIntegrityViolationException("uk_bet_member_match"));

        errorCode(() -> service.placeBet(7L, request(1_000)), ErrorCode.BET_ALREADY_PLACED);
        verify(coinTransactionService, never()).spendForBet(any(), anyLong(), anyInt());
        verify(txManager).rollback(any());
    }

    @Test
    @DisplayName("잔액 부족이면 LACK_COIN, Bet을 만들지 않고 롤백")
    void lackCoin() {
        when(membersRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(BetTestFixtures.member(7L, 999)));

        errorCode(() -> service.placeBet(7L, request(1_000)), ErrorCode.LACK_COIN);
        verify(betRepository, never()).saveAndFlush(any());
        verify(txManager).rollback(any());
    }

    @Test
    @DisplayName("회원 락 대기 시간(3초) 초과 시 409 BET_LOCK_TIMEOUT, 베팅 생성 안 함")
    void lockTimeout() {
        when(membersRepository.findByIdForUpdate(7L)).thenThrow(new CannotAcquireLockException("Lock wait timeout exceeded"));

        errorCode(() -> service.placeBet(7L, request(1_000)), ErrorCode.BET_LOCK_TIMEOUT);
        assertThat(ErrorCode.BET_LOCK_TIMEOUT.getStatus().value()).isEqualTo(409);
        verify(betRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("없는 경기 / 없는 회원")
    void notFound() {
        when(matchesRepository.findById(5L)).thenReturn(Optional.empty());
        when(membersRepository.findByIdForUpdate(8L)).thenReturn(Optional.empty());

        errorCode(() -> service.placeBet(7L, new PlaceBetRequest(5L, Side.HOME, 1_000)), ErrorCode.MATCH_NOT_FOUND);
        errorCode(() -> service.placeBet(8L, request(1_000)), ErrorCode.MEMBER_NOT_FOUND);
    }
}
