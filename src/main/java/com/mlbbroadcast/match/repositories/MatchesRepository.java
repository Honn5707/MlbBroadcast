package com.mlbbroadcast.match.repositories;

import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface MatchesRepository extends JpaRepository<Matches, Long> {

    //수정용 조회: 비관적 쓰기 락(SELECT ... FOR UPDATE). 트랜잭션이 끝날 때까지 다른 트랜잭션의 같은 경기 수정은 대기
    //경기 상태/필드를 바꾸는 코드는 반드시 이 메서드로 조회 (트랜잭션 밖에서 호출하면 예외)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Matches m WHERE m.id = :id")
    Optional<Matches> findByIdForUpdate(@Param("id") Long id);

    boolean existsByGamePk(int gamePk);

    @Query("SELECT m.gamePk FROM Matches m where m.gamePk IN :game_pk")
    Set<Integer> findExistingGamePk(@Param("game_pk") List<Integer> gamePks);

    List<Matches> findAllByMatchStatus(MatchStatus matchStatus);

    List<Matches> findAllByMatchStatusIn(Collection<MatchStatus> matchStatuses);

    List<Matches> findAllByGamePkIn(Collection<Integer> gamePks);

    //일정 조회: [start, end) 시각 범위 (UTC)
    List<Matches> findAllByScheduledStartedTimeGreaterThanEqualAndScheduledStartedTimeLessThanOrderByScheduledStartedTimeAsc(LocalDateTime start, LocalDateTime end);

    Optional<Matches> findByGamePk(int gamePk);
}
