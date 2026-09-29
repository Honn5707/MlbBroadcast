package com.mlbbroadcast.match.repositories;

import com.mlbbroadcast.match.entities.MatchplayLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface MatchPlaylogRepository extends JpaRepository<MatchplayLog, Long> {



    @Query("SELECT MAX(m.id) FROM MatchplayLog m WHERE m.matchId = :matchId")
    Optional<MatchplayLog> findByLatestMatchPlayLog(@Param("matchId") Long matchId);

    @Query("SELECT MAX(m.inning)  FROM MatchplayLog m WHERE m.matchId = :matchId")
    Optional<Integer> findByBiggestAtBatInning(@Param("matchId") Long matchId);


}
