package org.example.mlbbroadcast.bet;

import com.mlbbroadcast.bet.BetRepository;
import com.mlbbroadcast.bet.BetScanService;
import com.mlbbroadcast.bet.settlement.BetSettlementService;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import org.example.mlbbroadcast.match.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BetScanServiceTest {
    @Mock MatchesRepository matchesRepository;
    @Mock BetRepository betRepository;
    @Mock BetSettlementService settlementService;
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    BetScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new BetScanService(matchesRepository, betRepository, settlementService, BetTestFixtures.properties(),
                new TransactionTemplate(txManager), new MutableClock(Instant.parse("2026-11-20T10:00:00Z")));
    }

    @Test
    @DisplayName("종료 후 5분 ~ 2일 이내 FINAL/CANCELED 경기를 조회한다")
    void scanWindow() {
        when(matchesRepository.findIdsEndedBetween(anyCollection(), any(), any())).thenReturn(List.of());

        scanService.scan();

        verify(matchesRepository).findIdsEndedBetween(anyCollection(),
                eq(LocalDateTime.of(2026, 11, 18, 10, 0)), eq(LocalDateTime.of(2026, 11, 20, 9, 55)));
    }

    @Test
    @DisplayName("PENDING/ERROR Bet이 남은 경기만 보정 정산(correction=true)")
    void onlyUnsettledMatches() {
        when(matchesRepository.findIdsEndedBetween(anyCollection(), any(), any())).thenReturn(List.of(1L, 2L));
        when(betRepository.existsByMatchIdAndStatusIn(eq(1L), anyCollection())).thenReturn(true);
        when(betRepository.existsByMatchIdAndStatusIn(eq(2L), anyCollection())).thenReturn(false);
        when(settlementService.settleMatch(1L, true)).thenReturn(new BetSettlementService.SettlementResult(1L, true, List.of()));

        scanService.scan();

        verify(settlementService).settleMatch(1L, true);
        verify(settlementService, never()).settleMatch(eq(2L), anyBoolean());
    }

    @Test
    @DisplayName("보정에 실패한 Bet은 retryCount 증가 + 한도(4회) 도달 시 FAILED (별도 트랜잭션)")
    void failedBetsCounted() {
        when(matchesRepository.findIdsEndedBetween(anyCollection(), any(), any())).thenReturn(List.of(1L));
        when(betRepository.existsByMatchIdAndStatusIn(eq(1L), anyCollection())).thenReturn(true);
        when(settlementService.settleMatch(1L, true)).thenReturn(new BetSettlementService.SettlementResult(1L, true, List.of(100L)));

        scanService.scan();

        verify(betRepository).incrementRetryCount(100L);
        verify(betRepository).markFailedIfExceeded(100L, 4);
        verify(txManager).commit(any());
    }

    @Test
    @DisplayName("한 경기의 보정이 예외로 실패해도 다음 경기를 계속 처리하고, 예외를 던지지 않는다")
    void exceptionsAreLogged() {
        when(matchesRepository.findIdsEndedBetween(anyCollection(), any(), any())).thenReturn(List.of(1L, 2L));
        when(betRepository.existsByMatchIdAndStatusIn(anyLong(), anyCollection())).thenReturn(true);
        when(settlementService.settleMatch(1L, true)).thenThrow(new RuntimeException("db down"));
        when(settlementService.settleMatch(2L, true)).thenReturn(new BetSettlementService.SettlementResult(2L, true, List.of()));

        assertThatCode(() -> scanService.scan()).doesNotThrowAnyException();
        verify(settlementService).settleMatch(2L, true);
    }
}
