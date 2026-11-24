package org.example.mlbbroadcast.bet;

import com.mlbbroadcast.bet.BetProperties;
import com.mlbbroadcast.bet.BetRetryWaiter;
import com.mlbbroadcast.bet.BetScanService;
import com.mlbbroadcast.bet.BetService;
import com.mlbbroadcast.bet.settlement.BetSettlementService;
import com.mlbbroadcast.coin.CoinTransactionService;
import org.example.mlbbroadcast.match.MutableClock;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ThreadLocalRandom;

//베팅 DB 통합 테스트용 최소 컨텍스트: JPA 슬라이스(@DataJpaTest) + 베팅/코인 서비스 (외부 API, 스케줄러, 웹 계층 제외)
@Configuration
@AutoConfigurationPackage(basePackages = "com.mlbbroadcast") //엔티티/리포지토리 스캔 대상
@EnableTransactionManagement
@Import({BetService.class, BetSettlementService.class, BetScanService.class, CoinTransactionService.class})
class BetDatabaseTestConfig {

    @Bean
    MutableClock clock() {
        return new MutableClock(AbstractBetDatabaseTest.NOW);
    }

    @Bean
    BetProperties betProperties() {
        return BetTestFixtures.properties();
    }

    //재시도 대기는 짧게 (10 ~ 30ms 난수). 락 대기 자체는 DB의 innodb_lock_wait_timeout이 결정
    @Bean
    BetRetryWaiter betRetryWaiter() {
        return () -> Thread.sleep(ThreadLocalRandom.current().nextLong(10, 31));
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }
}
