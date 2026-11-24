package org.example.mlbbroadcast.bet;

import com.mlbbroadcast.bet.Bet;
import com.mlbbroadcast.bet.BetRepository;
import com.mlbbroadcast.bet.BetStatus;
import com.mlbbroadcast.bet.settlement.BetSettlement;
import com.mlbbroadcast.bet.settlement.BetSettlementRepository;
import com.mlbbroadcast.bet.settlement.BetSettlementService;
import com.mlbbroadcast.coin.CoinTransaction;
import com.mlbbroadcast.coin.CoinTransactionRepository;
import com.mlbbroadcast.coin.CoinTransactionService;
import com.mlbbroadcast.coin.PointReason;
import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.member.Members;
import com.mlbbroadcast.member.MembersRepository;
import org.example.mlbbroadcast.match.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BetSettlementServiceTest {
    @Mock MatchesRepository matchesRepository;
    @Mock BetRepository betRepository;
    @Mock BetSettlementRepository settlementRepository;
    @Mock MembersRepository membersRepository;
    @Mock CoinTransactionService coinTransactionService;
    @Mock CoinTransactionRepository coinTransactionRepository;
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    AtomicInteger waits = new AtomicInteger();
    BetSettlementService service;

    static final Long MATCH_ID = 1L;
    static final Set<BetStatus> PENDING = Set.of(BetStatus.PENDING);
    static final Set<BetStatus> PENDING_OR_ERROR = Set.of(BetStatus.PENDING, BetStatus.ERROR);

    Members alice = BetTestFixtures.member(7L, 0);
    Members bob = BetTestFixtures.member(8L, 0);

    @BeforeEach
    void setUp() {
        service = new BetSettlementService(matchesRepository, betRepository, settlementRepository, membersRepository,
                coinTransactionService, coinTransactionRepository, BetTestFixtures.properties(), waits::incrementAndGet,
                new TransactionTemplate(txManager), new MutableClock(Instant.parse("2026-11-20T10:00:00Z")));
        when(membersRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(alice));
        when(membersRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(bob));
        when(betRepository.settle(anyLong(), any(), anyCollection(), any())).thenReturn(1);
        when(settlementRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Matches finalMatch(int home, int visit) {
        Matches match = BetTestFixtures.match(MATCH_ID, MatchStatus.LIVE, LocalDateTime.of(2026, 11, 20, 0, 0));
        match.finalizeResult(home, visit, LocalDateTime.of(2026, 11, 20, 3, 0));
        when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        return match;
    }

    private void bets(Set<BetStatus> target, Bet... bets) {
        when(betRepository.findAllByMatchIdAndStatusIn(MATCH_ID, target)).thenReturn(List.of(bets));
    }

    private void amounts(long total, long homeSide) {
        when(betRepository.sumAmountByMatchId(MATCH_ID)).thenReturn(total);
        when(betRepository.sumAmountByMatchIdAndSide(MATCH_ID, Side.HOME)).thenReturn(homeSide);
        when(betRepository.sumAmountByMatchIdAndSide(MATCH_ID, Side.VISIT)).thenReturn(total - homeSide);
    }

    @Nested
    @DisplayName("정산식")
    class Odds {
        @Test
        @DisplayName("배율 = 2.05 - w/t, 지급액 = 금액 x 배율(버림)")
        void formula() throws Exception {
            var odds = BetSettlementService.class.getDeclaredMethod("odds", long.class, long.class);
            var payout = BetSettlementService.class.getDeclaredMethod("payout", int.class, BigDecimal.class);
            odds.setAccessible(true);
            payout.setAccessible(true);

            assertThat(odds.invoke(null, 3_000L, 4_000L)).isEqualTo(new BigDecimal("1.3000"));   //2.05 - 0.75
            assertThat(odds.invoke(null, 4_000L, 4_000L)).isEqualTo(new BigDecimal("1.0500"));   //모두 승리 측
            assertThat(odds.invoke(null, 1L, 3L)).isEqualTo(new BigDecimal("1.7166"));           //2.05 - 0.3333.. (버림)
            assertThat(payout.invoke(null, 1_001, new BigDecimal("1.7166"))).isEqualTo(1_718); //1718.31 -> 1718
        }

        @Test
        @DisplayName("bet_settlement가 이미 있으면 다시 계산하지 않고 기존 배율 사용")
        void reuseExistingSettlement() {
            finalMatch(5, 3);
            when(settlementRepository.findByMatchId(MATCH_ID)).thenReturn(Optional.of(
                    BetSettlement.builder().matchId(MATCH_ID).winSide(Side.HOME).odds(new BigDecimal("1.5000")).build()));
            bets(PENDING, BetTestFixtures.bet(100L, MATCH_ID, 7L, 1_000, Side.HOME));

            service.settleMatch(MATCH_ID, false);

            verify(betRepository, never()).sumAmountByMatchId(anyLong());
            verify(settlementRepository, never()).saveAndFlush(any());
            verify(coinTransactionService).rewardBet(alice, 100L, 1_500);
        }

        @Test
        @DisplayName("동시에 생성되어 UNIQUE 위반이면 먼저 저장된 배율을 사용")
        void concurrentSettlementCreation() {
            finalMatch(5, 3);
            amounts(4_000, 3_000);
            when(settlementRepository.findByMatchId(MATCH_ID)).thenReturn(Optional.empty(),
                    Optional.of(BetSettlement.builder().matchId(MATCH_ID).winSide(Side.HOME).odds(new BigDecimal("1.3000")).build()));
            when(settlementRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_bet_settlement_match"));
            bets(PENDING, BetTestFixtures.bet(100L, MATCH_ID, 7L, 1_000, Side.HOME));

            service.settleMatch(MATCH_ID, false);

            verify(coinTransactionService).rewardBet(alice, 100L, 1_300);
        }

        @Test
        @DisplayName("베팅이 하나도 없으면 bet_settlement를 만들지 않는다 (t = 0, 0으로 나누기 방지)")
        void noBets() {
            finalMatch(5, 3);
            amounts(0, 0);
            bets(PENDING);

            BetSettlementService.SettlementResult result = service.settleMatch(MATCH_ID, false);

            assertThat(result.settled()).isTrue();
            verify(settlementRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("WON / LOST")
    class WinLose {
        @Test
        @DisplayName("승리: 회원 락 -> 조건부 UPDATE(WON) -> BET_REWARD 지급 / 패배: 회원 락 없이 조건부 UPDATE(LOST)만")
        void wonAndLost() {
            finalMatch(5, 3); //HOME 승
            amounts(4_000, 3_000); //배율 1.3
            Bet won = BetTestFixtures.bet(100L, MATCH_ID, 7L, 3_000, Side.HOME);
            Bet lost = BetTestFixtures.bet(101L, MATCH_ID, 8L, 1_000, Side.VISIT);
            bets(PENDING, won, lost);

            BetSettlementService.SettlementResult result = service.settleMatch(MATCH_ID, false);

            assertThat(result.failedBetIds()).isEmpty();
            InOrder order = inOrder(membersRepository, betRepository, coinTransactionService);
            order.verify(membersRepository).findByIdForUpdate(7L);
            order.verify(betRepository).settle(eq(100L), eq(BetStatus.WON), eq(PENDING), any());
            order.verify(coinTransactionService).rewardBet(alice, 100L, 3_900);
            verify(betRepository).settle(eq(101L), eq(BetStatus.LOST), eq(PENDING), any());
            verify(membersRepository, never()).findByIdForUpdate(8L);
            verify(coinTransactionService, never()).rewardBet(eq(bob), anyLong(), anyInt());

            ArgumentCaptor<BetSettlement> captor = ArgumentCaptor.forClass(BetSettlement.class);
            verify(settlementRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getOdds()).isEqualTo(new BigDecimal("1.3000"));
            assertThat(captor.getValue().getWinAmount()).isEqualTo(3_000);
            assertThat(captor.getValue().getTotalAmount()).isEqualTo(4_000);
        }

        @Test
        @DisplayName("조건부 UPDATE 영향 행이 0이면(이미 다른 정산이 처리) 코인을 지급하지 않는다 (이중 지급 방지)")
        void alreadySettled() {
            finalMatch(5, 3);
            amounts(4_000, 3_000);
            bets(PENDING, BetTestFixtures.bet(100L, MATCH_ID, 7L, 3_000, Side.HOME));
            when(betRepository.settle(anyLong(), any(), anyCollection(), any())).thenReturn(0);

            BetSettlementService.SettlementResult result = service.settleMatch(MATCH_ID, false);

            assertThat(result.failedBetIds()).isEmpty();
            verify(coinTransactionService, never()).rewardBet(any(), anyLong(), anyInt());
        }

        @Test
        @DisplayName("보정(correction)은 PENDING과 ERROR를 대상으로 조건부 UPDATE")
        void correctionTargetsError() {
            finalMatch(5, 3);
            amounts(4_000, 3_000);
            bets(PENDING_OR_ERROR, BetTestFixtures.bet(100L, MATCH_ID, 7L, 3_000, Side.HOME));

            service.settleMatch(MATCH_ID, true);

            verify(betRepository).findAllByMatchIdAndStatusIn(MATCH_ID, PENDING_OR_ERROR);
            verify(betRepository).settle(eq(100L), eq(BetStatus.WON), eq(PENDING_OR_ERROR), any());
        }
    }

    @Nested
    @DisplayName("환불")
    class Refund {
        @Test
        @DisplayName("경기 취소: CANCELED + 베팅 금액 환불(BET_REFUND), 배율 계산 없음")
        void canceled() {
            Matches match = BetTestFixtures.match(MATCH_ID, MatchStatus.CANCELED, LocalDateTime.of(2026, 11, 20, 0, 0));
            when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
            bets(PENDING, BetTestFixtures.bet(100L, MATCH_ID, 7L, 2_000, Side.HOME), BetTestFixtures.bet(101L, MATCH_ID, 8L, 500, Side.VISIT));

            service.settleMatch(MATCH_ID, false);

            verify(betRepository).settle(eq(100L), eq(BetStatus.CANCELED), eq(PENDING), any());
            verify(coinTransactionService).refundBet(alice, 100L, 2_000);
            verify(coinTransactionService).refundBet(bob, 101L, 500);
            verifyNoInteractions(settlementRepository);
        }

        @Test
        @DisplayName("FINAL인데 승리 측이 NONE(동점): NONE + 환불")
        void tie() {
            finalMatch(4, 4);
            bets(PENDING, BetTestFixtures.bet(100L, MATCH_ID, 7L, 2_000, Side.HOME));

            service.settleMatch(MATCH_ID, false);

            verify(betRepository).settle(eq(100L), eq(BetStatus.NONE), eq(PENDING), any());
            verify(coinTransactionService).refundBet(alice, 100L, 2_000);
        }

        @Test
        @DisplayName("FINAL/CANCELED가 아닌 경기(LIVE, 상태 불명 등)는 정산하지 않는다")
        void notEnded() {
            when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.of(
                    BetTestFixtures.match(MATCH_ID, MatchStatus.MATCH_SUSPENDED_UNKNOWN, LocalDateTime.of(2026, 11, 20, 0, 0))));
            bets(PENDING, BetTestFixtures.bet(100L, MATCH_ID, 7L, 2_000, Side.HOME));

            BetSettlementService.SettlementResult result = service.settleMatch(MATCH_ID, false);

            assertThat(result.settled()).isFalse();
            verify(betRepository, never()).settle(anyLong(), any(), anyCollection(), any());
        }
    }

    @Nested
    @DisplayName("락 실패 / 예외")
    class Failures {
        @Test
        @DisplayName("락 획득 실패 시 대기 후 해당 Bet만 재시도, 성공하면 종료")
        void retryThenSuccess() {
            finalMatch(5, 3);
            amounts(4_000, 3_000);
            bets(PENDING, BetTestFixtures.bet(100L, MATCH_ID, 7L, 3_000, Side.HOME));
            when(membersRepository.findByIdForUpdate(7L))
                    .thenThrow(new CannotAcquireLockException("timeout"))
                    .thenThrow(new CannotAcquireLockException("timeout"))
                    .thenReturn(Optional.of(alice));

            BetSettlementService.SettlementResult result = service.settleMatch(MATCH_ID, false);

            assertThat(result.failedBetIds()).isEmpty();
            assertThat(waits).hasValue(2);
            verify(coinTransactionService, times(1)).rewardBet(alice, 100L, 3_900);
            verify(betRepository, never()).markError(anyLong());
        }

        @Test
        @DisplayName("5회 모두 락 획득 실패: 해당 Bet만 ERROR(별도 트랜잭션), 다른 Bet 정산은 계속")
        void fiveFailuresMarkError() {
            finalMatch(5, 3);
            amounts(4_000, 3_000);
            Bet blocked = BetTestFixtures.bet(100L, MATCH_ID, 7L, 3_000, Side.HOME);
            Bet other = BetTestFixtures.bet(101L, MATCH_ID, 8L, 1_000, Side.HOME);
            bets(PENDING, blocked, other);
            when(membersRepository.findByIdForUpdate(7L)).thenThrow(new CannotAcquireLockException("timeout"));

            BetSettlementService.SettlementResult result = service.settleMatch(MATCH_ID, false);

            assertThat(result.failedBetIds()).containsExactly(100L);
            verify(membersRepository, times(5)).findByIdForUpdate(7L);
            assertThat(waits).hasValue(4); //시도 사이 대기 4번
            verify(betRepository).markError(100L);
            verify(coinTransactionService).rewardBet(bob, 101L, 1_300);
        }

        @Test
        @DisplayName("락 실패가 아닌 DB 장애는 ERROR로 표시하지 않고 예외를 던진다 (보정 스캔에서 재처리)")
        void infrastructureFailure() {
            finalMatch(5, 3);
            amounts(4_000, 3_000);
            bets(PENDING, BetTestFixtures.bet(100L, MATCH_ID, 7L, 3_000, Side.HOME));
            when(membersRepository.findByIdForUpdate(7L)).thenThrow(new DataAccessResourceFailureException("db down"));

            assertThatThrownBy(() -> service.settleMatch(MATCH_ID, false)).isInstanceOf(DataAccessResourceFailureException.class);
            verify(betRepository, never()).markError(anyLong());
        }

        @Test
        @DisplayName("(bet_id, BET_REWARD) UNIQUE 위반: 기존 기록 금액이 같으면 코인 변경 없이 상태만 정정")
        void duplicateRecordSameAmount() {
            finalMatch(5, 3);
            amounts(4_000, 3_000);
            bets(PENDING, BetTestFixtures.bet(100L, MATCH_ID, 7L, 3_000, Side.HOME));
            doThrow(new DataIntegrityViolationException("uk_coin_transaction_bet_reason")).when(coinTransactionService).rewardBet(alice, 100L, 3_900);
            when(coinTransactionRepository.findByBetIdAndPointReason(100L, PointReason.BET_REWARD)).thenReturn(Optional.of(
                    CoinTransaction.builder().betId(100L).memberId(7L).pointReason(PointReason.BET_REWARD).amount(3_900).build()));

            BetSettlementService.SettlementResult result = service.settleMatch(MATCH_ID, false);

            assertThat(result.failedBetIds()).isEmpty();
            verify(betRepository, times(2)).settle(eq(100L), eq(BetStatus.WON), eq(PENDING), any()); //롤백된 1회 + 상태 정정 1회
            verify(coinTransactionService, times(1)).rewardBet(any(), anyLong(), anyInt());
        }

        @Test
        @DisplayName("(bet_id, BET_REWARD) UNIQUE 위반인데 금액이 다르면 불일치로 실패 처리 (ERROR 아님)")
        void duplicateRecordDifferentAmount() {
            finalMatch(5, 3);
            amounts(4_000, 3_000);
            bets(PENDING, BetTestFixtures.bet(100L, MATCH_ID, 7L, 3_000, Side.HOME));
            doThrow(new DataIntegrityViolationException("uk")).when(coinTransactionService).rewardBet(alice, 100L, 3_900);
            when(coinTransactionRepository.findByBetIdAndPointReason(100L, PointReason.BET_REWARD)).thenReturn(Optional.of(
                    CoinTransaction.builder().betId(100L).memberId(7L).pointReason(PointReason.BET_REWARD).amount(1).build()));

            BetSettlementService.SettlementResult result = service.settleMatch(MATCH_ID, false);

            assertThat(result.failedBetIds()).containsExactly(100L);
            verify(betRepository, never()).markError(anyLong());
        }
    }
}
