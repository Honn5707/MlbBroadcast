package org.example.mlbbroadcast.bet;

import com.mlbbroadcast.bet.Bet;
import com.mlbbroadcast.bet.BetProperties;
import com.mlbbroadcast.bet.BetRepository;
import com.mlbbroadcast.bet.BetScanService;
import com.mlbbroadcast.bet.BetService;
import com.mlbbroadcast.bet.BetStatus;
import com.mlbbroadcast.bet.settlement.BetSettlement;
import com.mlbbroadcast.bet.settlement.BetSettlementRepository;
import com.mlbbroadcast.bet.settlement.BetSettlementService;
import com.mlbbroadcast.coin.CoinTransaction;
import com.mlbbroadcast.coin.CoinTransactionRepository;
import com.mlbbroadcast.coin.PointReason;
import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.member.Members;
import com.mlbbroadcast.member.MembersRepository;
import com.mlbbroadcast.member.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 베팅 DB 통합 테스트 공통 기반
 *
 * - DB: Testcontainers MySQL 8.4 (JVM당 컨테이너 1개를 모든 하위 테스트 클래스가 공유)
 * - 스키마: Flyway 마이그레이션(src/main/resources/db/migration)으로 생성, ddl-auto=validate로 엔티티 매핑과 일치하는지 검증
 * - 트랜잭션: @DataJpaTest의 테스트 트랜잭션을 끔 (각 스레드가 실제로 커밋하고 락을 경합해야 함). 대신 매 테스트 전에 테이블을 비움
 * - 락 대기 한도: application.yml의 connection-init-sql(innodb_lock_wait_timeout = 3) 그대로. 하위 클래스가 @TestPropertySource로 바꿀 수 있음
 */
@Tag("db")
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=40"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = BetDatabaseTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class AbstractBetDatabaseTest {

    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    static final LocalDateTime NOW_UTC = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    static final LocalDateTime START = NOW_UTC.plusHours(2);
    static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;
    static final int MYSQL_DEADLOCK = 1213;

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start(); //컨텍스트 캐시가 여러 클래스에 걸쳐 재사용되므로 클래스 단위(@Container)가 아니라 JVM 단위로 기동
    }

    @Autowired BetService betService;
    @Autowired BetSettlementService settlementService;
    @Autowired BetScanService scanService;
    @Autowired BetRepository betRepository;
    @Autowired BetSettlementRepository settlementRepository;
    @Autowired CoinTransactionRepository coinTransactionRepository;
    @Autowired MembersRepository membersRepository;
    @Autowired MatchesRepository matchesRepository;
    @Autowired BetProperties properties;
    @Autowired TransactionTemplate tx;

    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void cleanUpDatabase() {
        coinTransactionRepository.deleteAllInBatch();
        settlementRepository.deleteAllInBatch();
        betRepository.deleteAllInBatch();
        membersRepository.deleteAllInBatch();
        matchesRepository.deleteAllInBatch();
        //테스트에서 바꾼 설정 복구 (컨텍스트가 공유되므로)
        BetProperties defaults = BetTestFixtures.properties();
        properties.setLockRetryMaxAttempts(defaults.getLockRetryMaxAttempts());
        properties.setMaxCorrectionAttempts(defaults.getMaxCorrectionAttempts());
    }

    // ===================================================================== 정합성 불변식

    //모든 회원/Bet에 대해:
    //1) 잔액 >= 0, 잔액 == 해당 회원 coin_transaction 합계 (기록 없는 잔액 변경 없음)
    //2) Bet마다 BET_PLACED(-금액) 정확히 1건
    //3) WON -> BET_REWARD 1건 (금액 = 금액 x 저장된 배율), CANCELED/NONE -> BET_REFUND(+금액) 1건, 그 외 -> 지급/환불 없음
    void assertLedgerConsistent() {
        List<CoinTransaction> all = coinTransactionRepository.findAll();
        Map<Long, Integer> sumByMember = all.stream()
                .collect(Collectors.groupingBy(CoinTransaction::getMemberId, Collectors.summingInt(CoinTransaction::getAmount)));
        for (Members member : membersRepository.findAll()) {
            assertThat(member.getCoinRemain()).as("member %d 잔액 >= 0", member.getId()).isGreaterThanOrEqualTo(0);
            assertThat(member.getCoinRemain()).as("member %d 잔액 == 기록 합계", member.getId())
                    .isEqualTo(sumByMember.getOrDefault(member.getId(), 0));
        }
        for (Bet bet : betRepository.findAll()) {
            List<CoinTransaction> records = all.stream().filter(t -> bet.getId().equals(t.getBetId())).toList();
            assertThat(records).as("bet %d BET_PLACED", bet.getId())
                    .filteredOn(t -> t.getPointReason() == PointReason.BET_PLACED)
                    .extracting(CoinTransaction::getAmount).containsExactly(-bet.getAmount());
            List<CoinTransaction> rewards = records.stream().filter(t -> t.getPointReason() == PointReason.BET_REWARD).toList();
            List<CoinTransaction> refunds = records.stream().filter(t -> t.getPointReason() == PointReason.BET_REFUND).toList();
            switch (bet.getStatus()) {
                case WON -> {
                    BigDecimal odds = settlementRepository.findByMatchId(bet.getMatchId()).orElseThrow().getOdds();
                    assertThat(rewards).as("bet %d 지급", bet.getId()).extracting(CoinTransaction::getAmount)
                            .containsExactly(payout(bet.getAmount(), odds));
                    assertThat(refunds).isEmpty();
                }
                case CANCELED, NONE -> {
                    assertThat(refunds).as("bet %d 환불", bet.getId()).extracting(CoinTransaction::getAmount).containsExactly(bet.getAmount());
                    assertThat(rewards).isEmpty();
                }
                default -> {
                    assertThat(rewards).as("bet %d(%s) 지급 없음", bet.getId(), bet.getStatus()).isEmpty();
                    assertThat(refunds).as("bet %d(%s) 환불 없음", bet.getId(), bet.getStatus()).isEmpty();
                }
            }
        }
    }

    // ===================================================================== 동시 실행

    record Outcome<T>(T value, Throwable error, long elapsedMillis) {}

    //모든 작업을 동시에 출발시키고 결과(성공 값 또는 예외, 소요 시간)를 수집
    <T> List<Outcome<T>> runConcurrently(int count, IntFunction<Callable<T>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<T> callable = task.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    long begin = System.nanoTime();
                    try {
                        T value = callable.call();
                        return new Outcome<>(value, null, elapsedSince(begin));
                    } catch (Throwable e) {
                        return new Outcome<T>(null, e, elapsedSince(begin));
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) outcomes.add(future.get(90, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    static long elapsedSince(long beginNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beginNanos);
    }

    static <T> List<T> successes(List<Outcome<T>> outcomes) {
        return outcomes.stream().filter(o -> o.error() == null).map(Outcome::value).toList();
    }

    static <T> List<Throwable> failures(List<Outcome<T>> outcomes) {
        return outcomes.stream().map(Outcome::error).filter(e -> e != null).toList();
    }

    //실패는 모두 BusinessException이어야 함 (예상 못한 예외가 섞이면 실패)
    static <T> Map<ErrorCode, Long> errorCodes(List<Outcome<T>> outcomes) {
        List<Throwable> errors = failures(outcomes);
        assertThat(errors).allSatisfy(e -> assertThat(e).isInstanceOf(BusinessException.class));
        return errors.stream().collect(Collectors.groupingBy(e -> ((BusinessException) e).getErrorCode(), Collectors.counting()));
    }

    //예외 원인 사슬에서 MySQL 오류 코드 (없으면 null)
    static Integer sqlErrorCode(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() != 0) return sql.getErrorCode();
            if (t.getCause() == t) break;
        }
        return null;
    }

    // ===================================================================== 회원 락 점유

    //다른 트랜잭션이 회원 행에 FOR UPDATE를 잡고 있는 상황. close()(또는 releaseAfter 경과) 시 커밋
    MemberLock holdMemberLock(Long memberId) throws InterruptedException {
        return holdMemberLock(memberId, 60_000);
    }

    MemberLock holdMemberLock(Long memberId, long releaseAfterMillis) throws InterruptedException {
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> tx.executeWithoutResult(s -> {
            membersRepository.findByIdForUpdate(memberId).orElseThrow();
            acquired.countDown();
            try {
                release.await(releaseAfterMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }), "member-lock-holder-" + memberId);
        holder.start();
        assertThat(acquired.await(10, TimeUnit.SECONDS)).as("회원 락 선점").isTrue();
        return new MemberLock(holder, release);
    }

    record MemberLock(Thread holder, CountDownLatch release) implements AutoCloseable {
        @Override public void close() throws InterruptedException {
            release.countDown();
            holder.join(10_000);
        }
    }

    // ===================================================================== 데이터 준비

    //회원 + 초기 지급 기록(CHARGE). 잔액 == 기록 합계 불변식의 시작점
    Long member(int coin) {
        int n = sequence.incrementAndGet();
        return tx.execute(s -> {
            Members member = Members.builder().providerId("it-" + n + "-" + System.nanoTime()).providerType(Provider.GOOGLE)
                    .nickname("it" + n + "-" + System.nanoTime()).createdAt(NOW_UTC).build();
            ReflectionTestUtils.setField(member, "coinRemain", coin);
            Long id = membersRepository.save(member).getId();
            coinTransactionRepository.save(coin(id, null, PointReason.CHARGE, coin));
            return id;
        });
    }

    Long scheduledMatch() {
        int n = sequence.incrementAndGet();
        return matchesRepository.saveAndFlush(Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(MatchStatus.SCHEDULED)
                .scheduledStartedTime(START).seasonYear("2026").gamePk(100_000 + n).build()).getId();
    }

    void finish(Long matchId, int home, int visit) {
        finishEndedBefore(matchId, home, visit, 0);
    }

    //보정 스캔 대상 구간(종료 후 5분 ~ 2일)에 넣으려면 minutesAgo >= 5
    void finishEndedBefore(Long matchId, int home, int visit, int minutesAgo) {
        tx.executeWithoutResult(s -> matchesRepository.findByIdForUpdate(matchId).orElseThrow()
                .finalizeResult(home, visit, NOW_UTC.minusMinutes(minutesAgo)));
    }

    void cancel(Long matchId) {
        tx.executeWithoutResult(s -> {
            Matches match = matchesRepository.findByIdForUpdate(matchId).orElseThrow();
            match.changeStatus(MatchStatus.CANCELED);
            match.matchEndTimeSet(NOW_UTC);
        });
    }

    int coinOf(Long memberId) {
        return membersRepository.findById(memberId).orElseThrow().getCoinRemain();
    }

    BetStatus statusOf(Long betId) {
        return betRepository.findById(betId).orElseThrow().getStatus();
    }

    List<CoinTransaction> transactions(Long memberId, PointReason reason) {
        return coinTransactionRepository.findAll().stream()
                .filter(t -> t.getMemberId().equals(memberId) && t.getPointReason() == reason).toList();
    }

    static Bet newBet(Long matchId, Long memberId, int amount, Side side) {
        return Bet.builder().matchId(matchId).memberId(memberId).amount(amount).betSide(side).createdAt(NOW_UTC).build();
    }

    static CoinTransaction coin(Long memberId, Long betId, PointReason reason, int amount) {
        return CoinTransaction.builder().memberId(memberId).betId(betId).pointReason(reason).amount(amount).createdAt(NOW_UTC).build();
    }

    static BetSettlement settlement(Long matchId, BigDecimal odds) {
        return BetSettlement.builder().matchId(matchId).winSide(Side.HOME).odds(odds).winAmount(1).totalAmount(1).createdAt(NOW_UTC).build();
    }

    //설계 문서의 정산식을 독립적으로 계산: 2.05 - w/t (소수 4자리 버림)
    static BigDecimal expectedOdds(long win, long total) {
        return new BigDecimal("2.05").subtract(BigDecimal.valueOf(win).divide(BigDecimal.valueOf(total), 10, RoundingMode.HALF_UP))
                .setScale(4, RoundingMode.DOWN);
    }

    static int payout(int amount, BigDecimal odds) {
        return BigDecimal.valueOf(amount).multiply(odds).setScale(0, RoundingMode.DOWN).intValueExact();
    }
}
