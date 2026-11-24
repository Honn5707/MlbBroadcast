package org.example.mlbbroadcast.bet;

import com.mlbbroadcast.bet.Bet;
import com.mlbbroadcast.bet.BetStatus;
import com.mlbbroadcast.bet.dto.BetResponse;
import com.mlbbroadcast.bet.dto.PlaceBetRequest;
import com.mlbbroadcast.bet.settlement.BetSettlement;
import com.mlbbroadcast.bet.settlement.BetSettlementService;
import com.mlbbroadcast.coin.CoinTransaction;
import com.mlbbroadcast.coin.PointReason;
import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.member.Members;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 베팅 시스템 DB 통합 테스트: 동시성 / 정합성 / 멱등성 / 락 실패 후 보정 복구
 * (Testcontainers MySQL + Flyway 스키마. 공통 설정은 AbstractBetDatabaseTest)
 *
 * 락 대기 시간 자체를 검증하는 테스트는 운영 설정(3초)으로 도는 BetLockTimeoutIntegrationTest에 있음.
 * 여기서는 락 실패가 여러 번 필요한 복구 시나리오의 실행 시간을 줄이려고 락 대기 한도를 1초로 낮춤.
 */
@TestPropertySource(properties = "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout = 1")
class BetDatabaseIntegrationTest extends AbstractBetDatabaseTest {

    static final int THREADS = 16;

    @Autowired JdbcTemplate jdbc;

    // ===================================================================== 스키마 (Flyway)

    @Nested
    @DisplayName("스키마: Flyway 마이그레이션")
    class Schema {

        @Test
        @DisplayName("V1(기준 스키마), V2(베팅 정합성 제약)가 모두 성공 적용되고, 엔티티 매핑과 일치(ddl-auto=validate로 컨텍스트 기동)")
        void migrationsApplied() {
            List<Map<String, Object>> history = jdbc.queryForList(
                    "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
            assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2");
            assertThat(history).allSatisfy(row -> assertThat(((Number) toNumber(row.get("success"))).intValue()).isEqualTo(1));
        }

        @Test
        @DisplayName("정합성을 지키는 UNIQUE 제약이 DB에 존재")
        void uniqueConstraintsExist() {
            List<String> uniques = jdbc.queryForList("""
                    SELECT CONCAT(table_name, '.', constraint_name) FROM information_schema.table_constraints
                    WHERE table_schema = DATABASE() AND constraint_type = 'UNIQUE'""", String.class);
            assertThat(uniques).contains(
                    "bet.uk_bet_member_match",
                    "coin_transaction.uk_coin_transaction_bet_reason",
                    "bet_settlement.uk_bet_settlement_match");
        }

        private Object toNumber(Object value) {
            return value instanceof Boolean b ? (b ? 1 : 0) : value;
        }
    }

    // ===================================================================== 동시성

    @Nested
    @DisplayName("동시성")
    class Concurrency {

        @Test
        @DisplayName("같은 회원이 같은 경기에 동시에 베팅: 1건만 성공, 나머지는 BET_ALREADY_PLACED, 코인은 한 번만 차감")
        void sameMemberSameMatch() throws Exception {
            Long memberId = member(10_000);
            Long matchId = scheduledMatch();

            List<Outcome<BetResponse>> outcomes = runConcurrently(THREADS,
                    i -> () -> betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 1_000)));

            assertThat(successes(outcomes)).hasSize(1);
            assertThat(errorCodes(outcomes)).containsOnlyKeys(ErrorCode.BET_ALREADY_PLACED)
                    .containsEntry(ErrorCode.BET_ALREADY_PLACED, (long) THREADS - 1);
            assertThat(betRepository.findAll()).hasSize(1);
            assertThat(coinOf(memberId)).isEqualTo(9_000);
            assertThat(transactions(memberId, PointReason.BET_PLACED)).hasSize(1);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("같은 회원이 여러 경기에 동시에 베팅 (잔액 부족 경계): 잔액이 허용하는 만큼만 성공, 잔액은 음수가 되지 않음 (Lost Update 없음)")
        void sameMemberManyMatchesWithLimitedCoin() throws Exception {
            Long memberId = member(1_000);
            List<Long> matchIds = new ArrayList<>();
            for (int i = 0; i < 10; i++) matchIds.add(scheduledMatch());

            List<Outcome<BetResponse>> outcomes = runConcurrently(matchIds.size(),
                    i -> () -> betService.placeBet(memberId, new PlaceBetRequest(matchIds.get(i), Side.VISIT, 300)));

            assertThat(successes(outcomes)).hasSize(3);
            assertThat(errorCodes(outcomes)).containsOnlyKeys(ErrorCode.LACK_COIN)
                    .containsEntry(ErrorCode.LACK_COIN, 7L);
            assertThat(coinOf(memberId)).isEqualTo(100);
            assertThat(betRepository.findAll()).hasSize(3);
            //각 성공 응답의 잔액은 서로 달라야 함 (같은 잔액을 읽고 덮어쓰지 않았음)
            assertThat(successes(outcomes)).extracting(BetResponse::coinRemain).containsExactlyInAnyOrder(700, 400, 100);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("여러 회원이 같은 경기에 동시에 베팅: 모두 성공, 회원별 차감/기록이 정확")
        void manyMembersSameMatch() throws Exception {
            Long matchId = scheduledMatch();
            List<Long> memberIds = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) memberIds.add(member(5_000));

            List<Outcome<BetResponse>> outcomes = runConcurrently(THREADS, i -> () -> betService.placeBet(memberIds.get(i),
                    new PlaceBetRequest(matchId, i % 2 == 0 ? Side.HOME : Side.VISIT, 100 * (i + 1))));

            assertThat(successes(outcomes)).hasSize(THREADS);
            for (int i = 0; i < THREADS; i++) assertThat(coinOf(memberIds.get(i))).isEqualTo(5_000 - 100 * (i + 1));
            assertThat(betRepository.sumAmountByMatchId(matchId)).isEqualTo(100L * THREADS * (THREADS + 1) / 2);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("같은 경기를 이벤트 정산과 보정 스캔이 동시에 여러 번 실행: Bet당 지급은 한 번, bet_settlement는 한 행")
        void concurrentSettlement() throws Exception {
            Long matchId = scheduledMatch();
            List<Long> memberIds = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                Long memberId = member(10_000);
                memberIds.add(memberId);
                betService.placeBet(memberId, new PlaceBetRequest(matchId, i < 4 ? Side.HOME : Side.VISIT, 1_000));
            }
            finish(matchId, 5, 3); //HOME 승: w = 4,000, t = 12,000

            List<Outcome<BetSettlementService.SettlementResult>> outcomes = runConcurrently(THREADS,
                    i -> () -> settlementService.settleMatch(matchId, i % 2 == 1));

            assertThat(failures(outcomes)).isEmpty();
            assertThat(outcomes).allSatisfy(o -> assertThat(o.value().failedBetIds()).isEmpty());
            assertThat(settlementRepository.findAll()).hasSize(1);
            BetSettlement settlement = settlementRepository.findByMatchId(matchId).orElseThrow();
            assertThat(settlement.getWinAmount()).isEqualTo(4_000);
            assertThat(settlement.getTotalAmount()).isEqualTo(12_000);
            assertThat(settlement.getOdds()).isEqualByComparingTo(expectedOdds(4_000, 12_000)); //1.7166

            int payout = payout(1_000, settlement.getOdds());
            for (int i = 0; i < memberIds.size(); i++) {
                Long memberId = memberIds.get(i);
                if (i < 4) {
                    assertThat(transactions(memberId, PointReason.BET_REWARD)).hasSize(1);
                    assertThat(coinOf(memberId)).isEqualTo(10_000 - 1_000 + payout);
                } else {
                    assertThat(transactions(memberId, PointReason.BET_REWARD)).isEmpty();
                    assertThat(coinOf(memberId)).isEqualTo(9_000);
                }
            }
            assertThat(betRepository.findAll()).extracting(Bet::getStatus).containsOnly(BetStatus.WON, BetStatus.LOST);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("경기 취소 환불을 동시에 여러 번 실행: 환불은 Bet당 한 번, 잔액은 정확히 원래대로")
        void concurrentRefund() throws Exception {
            Long matchId = scheduledMatch();
            List<Long> memberIds = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Long memberId = member(3_000);
                memberIds.add(memberId);
                betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 500 + i * 100));
            }
            cancel(matchId);

            List<Outcome<BetSettlementService.SettlementResult>> outcomes = runConcurrently(THREADS,
                    i -> () -> settlementService.settleMatch(matchId, i % 2 == 0));

            assertThat(failures(outcomes)).isEmpty();
            for (Long memberId : memberIds) {
                assertThat(coinOf(memberId)).isEqualTo(3_000);
                assertThat(transactions(memberId, PointReason.BET_REFUND)).hasSize(1);
            }
            assertThat(betRepository.findAll()).extracting(Bet::getStatus).containsOnly(BetStatus.CANCELED);
            assertThat(settlementRepository.findAll()).isEmpty(); //취소는 배율 계산 없음
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("정산(지급)과 같은 회원의 새 베팅(차감)이 동시에 일어나도 회원 잔액이 덮어써지지 않음")
        void settlementAndPlaceBetOnSameMember() throws Exception {
            Long endedMatch = scheduledMatch();
            Long nextMatch = scheduledMatch();
            List<Long> memberIds = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Long memberId = member(10_000);
                memberIds.add(memberId);
                betService.placeBet(memberId, new PlaceBetRequest(endedMatch, Side.HOME, 2_000));
            }
            finish(endedMatch, 2, 1); //전원 HOME -> w = t -> 배율 1.05

            //스레드 0~3: 정산(이벤트/보정 혼합), 나머지: 각 회원의 다음 경기 베팅
            int settlers = 4;
            List<Outcome<Object>> outcomes = runConcurrently(settlers + memberIds.size(), i -> () -> i < settlers
                    ? settlementService.settleMatch(endedMatch, i % 2 == 1)
                    : betService.placeBet(memberIds.get(i - settlers), new PlaceBetRequest(nextMatch, Side.VISIT, 3_000)));

            assertThat(failures(outcomes)).isEmpty();
            int payout = payout(2_000, new BigDecimal("1.0500")); //2,100
            for (Long memberId : memberIds) {
                assertThat(coinOf(memberId)).isEqualTo(10_000 - 2_000 - 3_000 + payout);
            }
            assertLedgerConsistent();
        }
    }

    // ===================================================================== 정합성 (DB 제약 / 조건부 UPDATE)

    @Nested
    @DisplayName("정합성: DB 제약과 조건부 UPDATE")
    class Integrity {

        @Test
        @DisplayName("UNIQUE(member_id, match_id): 서비스를 거치지 않은 중복 Bet INSERT도 DB가 거절")
        void betUniqueConstraint() {
            Long memberId = member(1_000);
            Long matchId = scheduledMatch();
            betRepository.saveAndFlush(newBet(matchId, memberId, 100, Side.HOME));

            assertThatThrownBy(() -> betRepository.saveAndFlush(newBet(matchId, memberId, 200, Side.VISIT)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(betRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("UNIQUE(bet_id, point_reason): 같은 Bet의 같은 사유 기록은 한 번만 (bet_id가 NULL인 기록은 제약 대상 아님)")
        void coinTransactionUniqueConstraint() {
            Long memberId = member(1_000);
            coinTransactionRepository.saveAndFlush(coin(memberId, 99L, PointReason.BET_REWARD, 100));

            assertThatThrownBy(() -> coinTransactionRepository.saveAndFlush(coin(memberId, 99L, PointReason.BET_REWARD, 100)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            //같은 Bet이라도 사유가 다르면 허용
            coinTransactionRepository.saveAndFlush(coin(memberId, 99L, PointReason.BET_REFUND, 100));
            //bet_id NULL은 여러 건 허용 (가입 보너스/충전)
            coinTransactionRepository.saveAndFlush(coin(memberId, null, PointReason.CHARGE, 1));
            coinTransactionRepository.saveAndFlush(coin(memberId, null, PointReason.CHARGE, 1));
        }

        @Test
        @DisplayName("UNIQUE(match_id) bet_settlement: 경기당 배율은 한 행만")
        void settlementUniqueConstraint() {
            settlementRepository.saveAndFlush(settlement(1L, new BigDecimal("1.5000")));
            assertThatThrownBy(() -> settlementRepository.saveAndFlush(settlement(1L, new BigDecimal("1.9000"))))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("조건부 UPDATE(PENDING -> WON)를 동시에 실행해도 영향 행 합계는 1")
        void conditionalUpdateIsExclusive() throws Exception {
            Long memberId = member(1_000);
            Long betId = betRepository.saveAndFlush(newBet(scheduledMatch(), memberId, 100, Side.HOME)).getId();

            List<Outcome<Integer>> outcomes = runConcurrently(THREADS, i -> () ->
                    tx.execute(s -> betRepository.settle(betId, BetStatus.WON, List.of(BetStatus.PENDING), NOW_UTC)));

            assertThat(failures(outcomes)).isEmpty();
            assertThat(successes(outcomes).stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
            assertThat(statusOf(betId)).isEqualTo(BetStatus.WON);
        }

        @Test
        @DisplayName("정산된 Bet은 markError / incrementRetryCount / markFailedIfExceeded 대상이 아님 (상태 역전 방지)")
        void settledBetIsNotDowngraded() {
            Long memberId = member(1_000);
            Long betId = betRepository.saveAndFlush(newBet(scheduledMatch(), memberId, 100, Side.HOME)).getId();
            tx.execute(s -> betRepository.settle(betId, BetStatus.LOST, List.of(BetStatus.PENDING), NOW_UTC));

            Integer markError = tx.execute(s -> betRepository.markError(betId));
            Integer increment = tx.execute(s -> betRepository.incrementRetryCount(betId));
            Integer markFailed = tx.execute(s -> betRepository.markFailedIfExceeded(betId, 0));
            assertThat(markError).isZero();
            assertThat(increment).isZero();
            assertThat(markFailed).isZero();
            Bet bet = betRepository.findById(betId).orElseThrow();
            assertThat(bet.getStatus()).isEqualTo(BetStatus.LOST);
            assertThat(bet.getRetryCount()).isZero();
        }

        @Test
        @DisplayName("Bet INSERT 이후 코인 기록 단계에서 실패하면 Bet INSERT와 잔액 변경이 함께 롤백 (원자성)")
        void placeBetIsAtomic() {
            Long memberId = member(1_000);
            Long matchId = scheduledMatch();
            //다음 Bet id(AUTO_INCREMENT)를 알아낸 뒤, 그 id로 BET_PLACED 기록을 미리 넣어 spendForBet의 UNIQUE 위반을 유도
            Long probeId = betRepository.saveAndFlush(newBet(scheduledMatch(), memberId, 100, Side.HOME)).getId();
            betRepository.deleteById(probeId);
            coinTransactionRepository.saveAndFlush(coin(memberId, probeId + 1, PointReason.BET_PLACED, 0));

            assertThatThrownBy(() -> betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 500)))
                    .isInstanceOf(BusinessException.class);

            assertThat(betRepository.findAll()).as("Bet INSERT 롤백").isEmpty();
            assertThat(transactions(memberId, PointReason.BET_PLACED)).extracting(CoinTransaction::getAmount).containsExactly(0);
            assertThat(coinOf(memberId)).as("잔액 변경 롤백").isEqualTo(1_000);
        }
    }

    // ===================================================================== 멱등성

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("정산을 순차로 반복 실행(이벤트 -> 이벤트 -> 보정 -> 보정 스캔): 두 번째부터는 아무것도 변하지 않음")
        void repeatedSettlement() {
            Long matchId = scheduledMatch();
            Long winner = member(5_000);
            Long loser = member(5_000);
            betService.placeBet(winner, new PlaceBetRequest(matchId, Side.VISIT, 1_000));
            betService.placeBet(loser, new PlaceBetRequest(matchId, Side.HOME, 3_000));
            finishEndedBefore(matchId, 1, 4, 10); //VISIT 승: w = 1,000, t = 4,000 -> 1.8 (보정 스캔 대상 구간)

            settlementService.settleMatch(matchId, false);
            Snapshot first = snapshot();

            settlementService.settleMatch(matchId, false);
            settlementService.settleMatch(matchId, true);
            scanService.scan();

            assertThat(snapshot()).isEqualTo(first);
            assertThat(coinOf(winner)).isEqualTo(5_000 - 1_000 + 1_800);
            assertThat(coinOf(loser)).isEqualTo(2_000);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("배율은 한 번 저장되면 다시 계산하지 않음 (이후 금액 합이 바뀌어도 기존 배율로 지급)")
        void oddsAreFixedOnce() {
            Long matchId = scheduledMatch();
            Long a = member(5_000);
            betService.placeBet(a, new PlaceBetRequest(matchId, Side.HOME, 1_000));
            finish(matchId, 3, 0);
            //먼저 배율이 확정된 상태 (w = t = 1,000 -> 1.05)
            settlementRepository.saveAndFlush(settlement(matchId, new BigDecimal("1.0500")));
            //배율 확정 후 다른 Bet이 끼어든 상황을 강제로 만듦 (정상 경로에선 불가: 마감 이후)
            Long b = member(5_000);
            betRepository.saveAndFlush(newBet(matchId, b, 1_000, Side.VISIT));

            settlementService.settleMatch(matchId, false);

            assertThat(settlementRepository.findAll()).hasSize(1);
            assertThat(transactions(a, PointReason.BET_REWARD)).extracting(CoinTransaction::getAmount).containsExactly(1_050);
        }

        @Test
        @DisplayName("코인 기록은 있는데 Bet이 PENDING인 경우(금액 일치): 다시 지급하지 않고 상태만 WON으로 정정")
        void recordExistsWithSameAmount() {
            Long matchId = scheduledMatch();
            Long memberId = member(5_000);
            Long betId = betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 1_000)).betId();
            finish(matchId, 2, 0); //단독 베팅 -> 1.05 -> 1,050
            simulateRewardWithoutStatus(memberId, betId, 1_050);

            BetSettlementService.SettlementResult result = settlementService.settleMatch(matchId, false);

            assertThat(result.failedBetIds()).isEmpty();
            assertThat(statusOf(betId)).isEqualTo(BetStatus.WON);
            assertThat(transactions(memberId, PointReason.BET_REWARD)).hasSize(1);
            assertThat(coinOf(memberId)).isEqualTo(5_000 - 1_000 + 1_050);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("코인 기록은 있는데 금액이 다르면(불일치): 상태를 바꾸지 않고 실패로 남겨 수동 확인 대상이 됨")
        void recordExistsWithDifferentAmount() {
            Long matchId = scheduledMatch();
            Long memberId = member(5_000);
            Long betId = betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 1_000)).betId();
            finish(matchId, 2, 0);
            simulateRewardWithoutStatus(memberId, betId, 999);
            int before = coinOf(memberId);

            BetSettlementService.SettlementResult result = settlementService.settleMatch(matchId, false);

            assertThat(result.failedBetIds()).containsExactly(betId);
            assertThat(statusOf(betId)).isEqualTo(BetStatus.PENDING);
            assertThat(transactions(memberId, PointReason.BET_REWARD)).extracting(CoinTransaction::getAmount).containsExactly(999);
            assertThat(coinOf(memberId)).isEqualTo(before);
        }
    }

    // ===================================================================== 락 실패 후 보정 복구

    @Nested
    @DisplayName("락 실패 후 보정 복구")
    class LockFailureRecovery {

        @Test
        @DisplayName("정산 중 락 획득 재시도 모두 실패 -> 해당 Bet만 ERROR, 락 해제 후 보정 스캔이 한 번만 지급")
        void errorThenCorrection() throws Exception {
            properties.setLockRetryMaxAttempts(2);
            Long matchId = scheduledMatch();
            Long locked = member(5_000);
            Long free = member(5_000);
            Long lockedBet = betService.placeBet(locked, new PlaceBetRequest(matchId, Side.HOME, 1_000)).betId();
            betService.placeBet(free, new PlaceBetRequest(matchId, Side.HOME, 1_000));
            finishEndedBefore(matchId, 3, 1, 10);

            try (MemberLock ignored = holdMemberLock(locked)) {
                BetSettlementService.SettlementResult result = settlementService.settleMatch(matchId, false);
                assertThat(result.failedBetIds()).containsExactly(lockedBet);
            }
            assertThat(statusOf(lockedBet)).isEqualTo(BetStatus.ERROR);
            assertThat(coinOf(free)).isEqualTo(4_000 + 1_050); //다른 Bet은 영향 없음
            assertThat(coinOf(locked)).isEqualTo(4_000);

            scanService.scan();
            scanService.scan();

            assertThat(statusOf(lockedBet)).isEqualTo(BetStatus.WON);
            assertThat(transactions(locked, PointReason.BET_REWARD)).hasSize(1);
            assertThat(coinOf(locked)).isEqualTo(4_000 + 1_050);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("ERROR Bet은 이벤트 정산(PENDING 대상)으로는 처리되지 않고 보정(correction)으로만 처리")
        void errorIsOnlyHandledByCorrection() throws Exception {
            properties.setLockRetryMaxAttempts(1);
            Long matchId = scheduledMatch();
            Long memberId = member(5_000);
            Long betId = betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 1_000)).betId();
            finish(matchId, 1, 0);
            try (MemberLock ignored = holdMemberLock(memberId)) {
                settlementService.settleMatch(matchId, false);
            }
            assertThat(statusOf(betId)).isEqualTo(BetStatus.ERROR);

            settlementService.settleMatch(matchId, false);
            assertThat(statusOf(betId)).isEqualTo(BetStatus.ERROR);
            assertThat(transactions(memberId, PointReason.BET_REWARD)).isEmpty();

            settlementService.settleMatch(matchId, true);
            assertThat(statusOf(betId)).isEqualTo(BetStatus.WON);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("보정이 한도(4회)만큼 실패하면 FAILED로 고정, 이후 스캔은 더 이상 건드리지 않음 (코인 변화 없음)")
        void failedAfterMaxCorrections() throws Exception {
            properties.setLockRetryMaxAttempts(1);
            Long matchId = scheduledMatch();
            Long memberId = member(5_000);
            Long betId = betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.VISIT, 1_000)).betId();
            finishEndedBefore(matchId, 0, 1, 10);

            try (MemberLock ignored = holdMemberLock(memberId)) {
                settlementService.settleMatch(matchId, false); //-> ERROR
                for (int i = 0; i < properties.getMaxCorrectionAttempts(); i++) scanService.scan();
            }
            Bet bet = betRepository.findById(betId).orElseThrow();
            assertThat(bet.getStatus()).isEqualTo(BetStatus.FAILED);
            assertThat(bet.getRetryCount()).isEqualTo(properties.getMaxCorrectionAttempts());

            scanService.scan(); //락이 풀려도 FAILED는 보정 대상이 아님
            assertThat(statusOf(betId)).isEqualTo(BetStatus.FAILED);
            assertThat(transactions(memberId, PointReason.BET_REWARD)).isEmpty();
            assertThat(coinOf(memberId)).isEqualTo(4_000);
            assertLedgerConsistent();
        }
    }

    // ===================================================================== 헬퍼

    //지급 기록 + 잔액 반영은 커밋됐지만 Bet 상태는 PENDING으로 남은 상황 (수동 처리/과거 버전 등)
    private void simulateRewardWithoutStatus(Long memberId, Long betId, int amount) {
        tx.executeWithoutResult(s -> {
            Members member = membersRepository.findByIdForUpdate(memberId).orElseThrow();
            coinTransactionRepository.saveAndFlush(coin(memberId, betId, PointReason.BET_REWARD, amount));
            member.earnCoin(amount);
        });
    }

    record Snapshot(Map<Long, Integer> coins, Map<Long, BetStatus> statuses, long transactions, long settlements) {}

    private Snapshot snapshot() {
        return new Snapshot(
                membersRepository.findAll().stream().collect(Collectors.toMap(Members::getId, Members::getCoinRemain)),
                betRepository.findAll().stream().collect(Collectors.toMap(Bet::getId, Bet::getStatus)),
                coinTransactionRepository.count(),
                settlementRepository.count());
    }
}
