package org.example.mlbbroadcast.bet;

import com.mlbbroadcast.bet.BetStatus;
import com.mlbbroadcast.bet.LockFailures;
import com.mlbbroadcast.bet.dto.BetResponse;
import com.mlbbroadcast.bet.dto.PlaceBetRequest;
import com.mlbbroadcast.bet.settlement.BetSettlementService;
import com.mlbbroadcast.coin.PointReason;
import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 비관적 락 대기 한도(타임아웃) 통합 테스트
 *
 * 운영과 같은 설정으로 실행: application.yml의 hikari connection-init-sql "SET SESSION innodb_lock_wait_timeout = 3"
 * (MySQL은 쿼리 단위 FOR UPDATE WAIT n을 지원하지 않아 커넥션 단위로 3초를 건다. @QueryHints의 lock.timeout은 MySQL에서 무시됨)
 *
 * 검증 대상
 * - 설정이 풀의 모든 커넥션에 실제로 적용되는지
 * - 베팅: 3초 대기 후 BET_LOCK_TIMEOUT(부분 반영 없음), 한도 안에 풀리면 기다렸다가 성공
 * - 정산: 락 실패 시 Bet 단위 재시도, 재시도 중 풀리면 성공, 모두 실패하면 해당 Bet만 ERROR
 * - MySQL이 실제로 던지는 락 대기 초과(1205) / 교착(1213) 예외를 LockFailures가 락 실패로 판별하는지
 */
class BetLockTimeoutIntegrationTest extends AbstractBetDatabaseTest {

    static final long LOCK_WAIT_MILLIS = 3_000;
    //타이밍 허용 오차: 하한은 엄격하게(대기 한도보다 일찍 끝나면 설정이 안 먹은 것), 상한은 CI 지연을 고려해 넉넉하게
    static final long LOWER_SLACK = 200;
    static final long UPPER_SLACK = 2_500;

    @Autowired DataSource dataSource;

    @Nested
    @DisplayName("락 대기 한도 설정")
    class Setting {

        @Test
        @DisplayName("커넥션 풀의 모든 커넥션에 innodb_lock_wait_timeout = 3 이 적용됨 (기본값 50초가 아님)")
        void everyConnectionHasThreeSeconds() throws Exception {
            List<Connection> connections = new ArrayList<>();
            try {
                //동시에 여러 개를 빌려 새로 만들어진 커넥션까지 확인
                for (int i = 0; i < 10; i++) connections.add(dataSource.getConnection());
                for (Connection connection : connections) {
                    try (Statement st = connection.createStatement();
                         ResultSet rs = st.executeQuery("SELECT @@SESSION.innodb_lock_wait_timeout")) {
                        rs.next();
                        assertThat(rs.getInt(1)).isEqualTo(3);
                    }
                }
            } finally {
                for (Connection connection : connections) connection.close();
            }
        }
    }

    @Nested
    @DisplayName("베팅 생성")
    class PlaceBet {

        @Test
        @DisplayName("회원 락이 3초 넘게 잡혀 있으면 약 3초 대기 후 BET_LOCK_TIMEOUT, Bet/코인 기록/잔액 변화 없음")
        void timesOutAfterThreeSeconds() throws Exception {
            Long memberId = member(5_000);
            Long matchId = scheduledMatch();

            long elapsed;
            Throwable thrown;
            try (MemberLock ignored = holdMemberLock(memberId)) {
                long begin = System.nanoTime();
                thrown = catchThrowable(() -> betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 1_000)));
                elapsed = elapsedSince(begin);
            }

            assertThat(thrown).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.BET_LOCK_TIMEOUT);
            assertThat(elapsed).as("대기 시간(ms)").isBetween(LOCK_WAIT_MILLIS - LOWER_SLACK, LOCK_WAIT_MILLIS + UPPER_SLACK);
            assertThat(betRepository.findAll()).isEmpty();
            assertThat(transactions(memberId, PointReason.BET_PLACED)).isEmpty();
            assertThat(coinOf(memberId)).isEqualTo(5_000);

            //타임아웃 후 같은 요청을 다시 보내면 정상 처리 (중복으로 막히지 않음)
            BetResponse retried = betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 1_000));
            assertThat(retried.coinRemain()).isEqualTo(4_000);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("대기 한도 안(1초)에 락이 풀리면 타임아웃 없이 기다렸다가 성공")
        void waitsAndSucceedsWhenReleasedInTime() throws Exception {
            Long memberId = member(5_000);
            Long matchId = scheduledMatch();

            BetResponse response;
            long elapsed;
            try (MemberLock ignored = holdMemberLock(memberId, 1_000)) {
                long begin = System.nanoTime();
                response = betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 1_000));
                elapsed = elapsedSince(begin);
            }

            assertThat(response.status()).isEqualTo(BetStatus.PENDING);
            assertThat(elapsed).as("락 해제까지 대기(ms)").isBetween(1_000 - LOWER_SLACK, LOCK_WAIT_MILLIS - LOWER_SLACK);
            assertThat(coinOf(memberId)).isEqualTo(4_000);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("락이 잡힌 동안 같은 회원의 여러 경기 베팅이 동시에 들어오면 모두 BET_LOCK_TIMEOUT, 일부만 반영되지 않음")
        void concurrentRequestsAllTimeOut() throws Exception {
            Long memberId = member(10_000);
            List<Long> matchIds = new ArrayList<>();
            for (int i = 0; i < 5; i++) matchIds.add(scheduledMatch());

            List<Outcome<BetResponse>> outcomes;
            try (MemberLock ignored = holdMemberLock(memberId)) {
                outcomes = runConcurrently(matchIds.size(),
                        i -> () -> betService.placeBet(memberId, new PlaceBetRequest(matchIds.get(i), Side.HOME, 1_000)));
            }

            assertThat(errorCodes(outcomes)).containsOnlyKeys(ErrorCode.BET_LOCK_TIMEOUT)
                    .containsEntry(ErrorCode.BET_LOCK_TIMEOUT, (long) matchIds.size());
            assertThat(outcomes).allSatisfy(o -> assertThat(o.elapsedMillis()).isGreaterThanOrEqualTo(LOCK_WAIT_MILLIS - LOWER_SLACK));
            assertThat(betRepository.findAll()).isEmpty();
            assertThat(coinOf(memberId)).isEqualTo(10_000);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("다른 회원의 락은 영향 없음: 회원 A가 잠겨 있어도 회원 B의 베팅은 즉시 처리")
        void otherMembersAreNotBlocked() throws Exception {
            Long locked = member(5_000);
            Long other = member(5_000);
            Long matchId = scheduledMatch();

            long elapsed;
            try (MemberLock ignored = holdMemberLock(locked)) {
                long begin = System.nanoTime();
                betService.placeBet(other, new PlaceBetRequest(matchId, Side.VISIT, 1_000));
                elapsed = elapsedSince(begin);
            }

            assertThat(elapsed).isLessThan(1_000);
            assertThat(coinOf(other)).isEqualTo(4_000);
        }
    }

    @Nested
    @DisplayName("정산")
    class Settlement {

        @Test
        @DisplayName("첫 시도가 락 대기 초과로 실패해도 재시도 중 락이 풀리면 성공 (ERROR가 되지 않고, 지급은 한 번)")
        void retrySucceedsAfterRelease() throws Exception {
            Long matchId = scheduledMatch();
            Long memberId = member(5_000);
            Long betId = betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 1_000)).betId();
            finish(matchId, 1, 0);

            BetSettlementService.SettlementResult result;
            long elapsed;
            //4초 뒤 자동 해제: 첫 시도(3초 대기)는 실패, 두 번째 시도에서 락 획득
            try (MemberLock ignored = holdMemberLock(memberId, 4_000)) {
                long begin = System.nanoTime();
                result = settlementService.settleMatch(matchId, false);
                elapsed = elapsedSince(begin);
            }

            assertThat(elapsed).as("최소 한 번은 락 대기 초과").isGreaterThanOrEqualTo(LOCK_WAIT_MILLIS - LOWER_SLACK);
            assertThat(result.failedBetIds()).isEmpty();
            assertThat(statusOf(betId)).isEqualTo(BetStatus.WON);
            assertThat(transactions(memberId, PointReason.BET_REWARD)).hasSize(1);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("재시도(2회) 모두 락 대기 초과면 약 2 x 3초 후 해당 Bet만 ERROR, 같은 경기의 다른 Bet은 정상 정산")
        void allAttemptsFailMarksError() throws Exception {
            properties.setLockRetryMaxAttempts(2);
            Long matchId = scheduledMatch();
            Long locked = member(5_000);
            Long free = member(5_000);
            Long lockedBet = betService.placeBet(locked, new PlaceBetRequest(matchId, Side.HOME, 1_000)).betId();
            Long freeBet = betService.placeBet(free, new PlaceBetRequest(matchId, Side.HOME, 1_000)).betId();
            finish(matchId, 1, 0);

            BetSettlementService.SettlementResult result;
            long elapsed;
            try (MemberLock ignored = holdMemberLock(locked)) {
                long begin = System.nanoTime();
                result = settlementService.settleMatch(matchId, false);
                elapsed = elapsedSince(begin);
            }

            assertThat(elapsed).as("2회 x 3초 대기(ms)").isBetween(2 * LOCK_WAIT_MILLIS - LOWER_SLACK, 2 * LOCK_WAIT_MILLIS + UPPER_SLACK);
            assertThat(result.failedBetIds()).containsExactly(lockedBet);
            assertThat(statusOf(lockedBet)).isEqualTo(BetStatus.ERROR);
            assertThat(betRepository.findById(lockedBet).orElseThrow().getRetryCount()).as("이벤트 정산은 보정 횟수를 세지 않음").isZero();
            assertThat(statusOf(freeBet)).isEqualTo(BetStatus.WON);
            assertThat(coinOf(locked)).isEqualTo(4_000);
            assertLedgerConsistent();
        }

        @Test
        @DisplayName("LOST 정산은 회원 락을 잡지 않으므로 회원 락이 잡혀 있어도 대기 없이 처리")
        void lostDoesNotWaitForMemberLock() throws Exception {
            Long matchId = scheduledMatch();
            Long memberId = member(5_000);
            Long betId = betService.placeBet(memberId, new PlaceBetRequest(matchId, Side.HOME, 1_000)).betId();
            finish(matchId, 0, 2);

            long elapsed;
            BetSettlementService.SettlementResult result;
            try (MemberLock ignored = holdMemberLock(memberId)) {
                long begin = System.nanoTime();
                result = settlementService.settleMatch(matchId, false);
                elapsed = elapsedSince(begin);
            }

            assertThat(elapsed).isLessThan(1_000);
            assertThat(result.failedBetIds()).isEmpty();
            assertThat(statusOf(betId)).isEqualTo(BetStatus.LOST);
            assertLedgerConsistent();
        }
    }

    @Nested
    @DisplayName("MySQL 락 예외 판별 (LockFailures)")
    class LockExceptionClassification {

        @Test
        @DisplayName("회원 락 대기 초과 시 MySQL 1205 예외가 올라오고, LockFailures가 락 실패로 판별")
        void lockWaitTimeoutIsLockFailure() throws Exception {
            Long memberId = member(1_000);

            Throwable thrown;
            try (MemberLock ignored = holdMemberLock(memberId)) {
                thrown = catchThrowable(() -> tx.executeWithoutResult(s -> membersRepository.findByIdForUpdate(memberId)));
            }

            assertThat(thrown).isInstanceOf(PessimisticLockingFailureException.class);
            assertThat(sqlErrorCode(thrown)).isEqualTo(MYSQL_LOCK_WAIT_TIMEOUT);
            assertThat(LockFailures.isLockFailure(thrown)).isTrue();
        }

        @Test
        @DisplayName("교착 상태(A->B, B->A 순서로 회원 락)에서는 대기 한도 전 즉시 한쪽이 1213으로 롤백되고, LockFailures가 락 실패로 판별")
        void deadlockIsLockFailure() throws Exception {
            Long a = member(1_000);
            Long b = member(1_000);
            CyclicBarrier bothLockedFirst = new CyclicBarrier(2);

            List<Outcome<Void>> outcomes = runConcurrently(2, i -> () -> {
                Long first = i == 0 ? a : b;
                Long second = i == 0 ? b : a;
                tx.executeWithoutResult(s -> {
                    membersRepository.findByIdForUpdate(first).orElseThrow();
                    try {
                        bothLockedFirst.await(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    membersRepository.findByIdForUpdate(second).orElseThrow();
                });
                return null;
            });

            assertThat(successes(outcomes)).hasSize(1); //교착 희생자가 롤백되면 다른 쪽은 진행
            Throwable victim = failures(outcomes).getFirst();
            assertThat(sqlErrorCode(victim)).isEqualTo(MYSQL_DEADLOCK);
            assertThat(LockFailures.isLockFailure(victim)).isTrue();
            assertThat(outcomes).allSatisfy(o -> assertThat(o.elapsedMillis()).as("교착은 즉시 감지").isLessThan(LOCK_WAIT_MILLIS));
        }

        @Test
        @DisplayName("락 실패가 아닌 DB 예외(UNIQUE 위반)는 락 실패로 판별하지 않음 (ERROR 오표시 방지)")
        void integrityViolationIsNotLockFailure() {
            Long memberId = member(1_000);
            Long matchId = scheduledMatch();
            betRepository.saveAndFlush(newBet(matchId, memberId, 100, Side.HOME));

            Throwable thrown = catchThrowable(() -> betRepository.saveAndFlush(newBet(matchId, memberId, 100, Side.HOME)));

            assertThat(thrown).isNotNull();
            assertThat(LockFailures.isLockFailure(thrown)).isFalse();
            assertThatThrownBy(() -> { throw thrown; }).isNotInstanceOf(PessimisticLockingFailureException.class);
        }
    }
}
