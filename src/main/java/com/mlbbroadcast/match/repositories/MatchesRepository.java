package com.mlbbroadcast.match.repositories;

import com.mlbbroadcast.match.entities.Matches;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;


public interface MatchesRepository extends JpaRepository<Matches, Long> {

    boolean existsByExternalId(int externalId);

    @Query("SELECT m.externalId FROM Matches m where m.externalId IN: game_pk")
    Set<Integer> findExistingExternalIds(@Param("game_pk") List<Integer> gamePks);
}
