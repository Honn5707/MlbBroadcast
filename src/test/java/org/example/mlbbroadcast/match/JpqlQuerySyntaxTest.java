package org.example.mlbbroadcast.match;

import com.mlbbroadcast.match.entities.LineUp;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.entities.MatchplayLog;
import com.mlbbroadcast.match.entities.PlayEvent;
import com.mlbbroadcast.player.entity.PlayerHitterRecord;
import com.mlbbroadcast.player.entity.PlayerMaster;
import com.mlbbroadcast.player.entity.PlayerPitcherRecord;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;

//DB 없이 Hibernate를 띄워 엔티티 매핑과 @Query JPQL 문법/의미를 검증 (서버 구동 시 쿼리 검증 실패 방지)
class JpqlQuerySyntaxTest {
    static StandardServiceRegistry registry;
    static SessionFactory sessionFactory;

    @BeforeAll
    static void boot() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClasses(Matches.class, MatchplayLog.class, PlayEvent.class, LineUp.class, PlayerMaster.class,
                        PlayerHitterRecord.class, PlayerPitcherRecord.class,
                        com.mlbbroadcast.bet.Bet.class, com.mlbbroadcast.bet.settlement.BetSettlement.class,
                        com.mlbbroadcast.coin.CoinTransaction.class, com.mlbbroadcast.member.Members.class)
                .buildMetadata().buildSessionFactory();
    }

    @AfterAll
    static void close() {
        sessionFactory.close();
        StandardServiceRegistryBuilder.destroy(registry);
    }

    @ParameterizedTest
    @DisplayName("재매핑 UPDATE 쿼리 (MatchPlaylogRepository, LineUpRepository)")
    @ValueSource(strings = {
            "UPDATE MatchplayLog m SET m.batterId = (SELECT p.id FROM PlayerMaster p WHERE p.externalId = m.batterExternalId) WHERE m.batterId IS NULL AND EXISTS (SELECT 1 FROM PlayerMaster p2 WHERE p2.externalId = m.batterExternalId)",
            "UPDATE MatchplayLog m SET m.pitcherId = (SELECT p.id FROM PlayerMaster p WHERE p.externalId = m.pitcherExternalId) WHERE m.pitcherId IS NULL AND EXISTS (SELECT 1 FROM PlayerMaster p2 WHERE p2.externalId = m.pitcherExternalId)",
            "UPDATE LineUp l SET l.playerId = (SELECT p.id FROM PlayerMaster p WHERE p.externalId = l.externalId) WHERE l.playerId IS NULL AND EXISTS (SELECT 1 FROM PlayerMaster p2 WHERE p2.externalId = l.externalId)",
            "UPDATE PlayerHitterRecord r SET r.playerId = (SELECT p.id FROM PlayerMaster p WHERE p.externalId = r.playerExternalId) WHERE r.playerId IS NULL AND EXISTS (SELECT 1 FROM PlayerMaster p2 WHERE p2.externalId = r.playerExternalId)",
            "UPDATE Bet b SET b.status = :result, b.settledAt = :now WHERE b.id = :id AND b.status IN :from",
            "UPDATE Bet b SET b.status = com.mlbbroadcast.bet.BetStatus.ERROR WHERE b.id = :id AND b.status = com.mlbbroadcast.bet.BetStatus.PENDING",
            "UPDATE Bet b SET b.retryCount = b.retryCount + 1 WHERE b.id = :id AND b.status IN (com.mlbbroadcast.bet.BetStatus.PENDING, com.mlbbroadcast.bet.BetStatus.ERROR)",
            "UPDATE Bet b SET b.status = com.mlbbroadcast.bet.BetStatus.FAILED WHERE b.id = :id AND b.retryCount >= :limit AND b.status IN (com.mlbbroadcast.bet.BetStatus.PENDING, com.mlbbroadcast.bet.BetStatus.ERROR)",
            "UPDATE PlayerPitcherRecord r SET r.playerId = (SELECT p.id FROM PlayerMaster p WHERE p.externalId = r.playerExternalId) WHERE r.playerId IS NULL AND EXISTS (SELECT 1 FROM PlayerMaster p2 WHERE p2.externalId = r.playerExternalId)"
    })
    void updateQueries(String hql) {
        assertThatCode(() -> sessionFactory.inSession(session -> session.createMutationQuery(hql))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @DisplayName("조회 쿼리")
    @ValueSource(strings = {
            "SELECT m.atBatIndex FROM MatchplayLog m WHERE m.matchId = :matchId",
            "SELECT m.gamePk FROM Matches m where m.gamePk IN :game_pk",
            "SELECT m FROM Matches m WHERE m.id = :id",
            "SELECT m.id FROM Matches m WHERE m.matchStatus IN :statuses AND m.endedTime >= :from AND m.endedTime <= :to",
            "SELECT COALESCE(SUM(b.amount), 0) FROM Bet b WHERE b.matchId = :matchId",
            "SELECT COALESCE(SUM(b.amount), 0) FROM Bet b WHERE b.matchId = :matchId AND b.betSide = :side",
            "SELECT m FROM Members m WHERE m.id = :id"
    })
    void selectQueries(String hql) {
        assertThatCode(() -> sessionFactory.inSession(session -> session.createSelectionQuery(hql, Object.class))).doesNotThrowAnyException();
    }
}
