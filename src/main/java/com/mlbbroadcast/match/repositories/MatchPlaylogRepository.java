package com.mlbbroadcast.match.repositories;

import com.mlbbroadcast.match.entities.MatchplayLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;


public interface MatchPlaylogRepository extends JpaRepository<MatchplayLog, Long> {



    Optional<MatchplayLog> findFirstByMatchIdOrderByAtBatIndexDesc(Long matchId);

    @Query("SELECT m.atBatIndex FROM MatchplayLog m WHERE m.matchId = :matchId")
    Set<Integer> findAtBatIndexesByMatchId(@Param("matchId") Long matchId);

    //선수 동기화 후 마스터에 추가된 선수의 내부 id를 external_id로 재매핑
    @Modifying(clearAutomatically = true)
    @Query("UPDATE MatchplayLog m SET m.batterId = (SELECT p.id FROM PlayerMaster p WHERE p.externalId = m.batterExternalId) " +
            "WHERE m.batterId IS NULL AND EXISTS (SELECT 1 FROM PlayerMaster p2 WHERE p2.externalId = m.batterExternalId)")
    int remapNullBatterIds();

    @Modifying(clearAutomatically = true)
    @Query("UPDATE MatchplayLog m SET m.pitcherId = (SELECT p.id FROM PlayerMaster p WHERE p.externalId = m.pitcherExternalId) " +
            "WHERE m.pitcherId IS NULL AND EXISTS (SELECT 1 FROM PlayerMaster p2 WHERE p2.externalId = m.pitcherExternalId)")
    int remapNullPitcherIds();
    @Query("SELECT MAX(m.inning)  FROM MatchplayLog m WHERE m.matchId = :matchId")
    Optional<Integer> findByBiggestAtBatInning(@Param("matchId") Long matchId);


}
