package com.mlbbroadcast.match.repositories;

import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface MatchesRepository extends JpaRepository<Matches, Long> {

    boolean existsByGamePk(int gamePk);

    @Query("SELECT m.gamePk FROM Matches m where m.gamePk IN :game_pk")
    Set<Integer> findExistingGamePk(@Param("game_pk") List<Integer> gamePks);

    List<Matches> findAllByMatchStatus(MatchStatus matchStatus);

    Optional<Matches> findByGamePk(int gamePk);
}
