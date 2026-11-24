package com.mlbbroadcast.bet;

import com.mlbbroadcast.common.Side;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;


public interface BetRepository extends JpaRepository<Bet, Long> {

    boolean existsByMemberIdAndMatchId(Long memberId, Long matchId);

    List<Bet> findAllByMatchIdAndStatusIn(Long matchId, Collection<BetStatus> statuses);

    boolean existsByMatchIdAndStatusIn(Long matchId, Collection<BetStatus> statuses);

    //정산식용 금액 합 (BetStatus와 무관하게 경기의 모든 Bet)
    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bet b WHERE b.matchId = :matchId")
    long sumAmountByMatchId(@Param("matchId") Long matchId);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bet b WHERE b.matchId = :matchId AND b.betSide = :side")
    long sumAmountByMatchIdAndSide(@Param("matchId") Long matchId, @Param("side") Side side);

    //조건부 UPDATE: 현재 상태가 from에 포함될 때만 변경. 반환값(영향 행)이 1일 때만 코인 처리
    //clearAutomatically를 쓰지 않음: 같은 트랜잭션에서 먼저 락으로 조회한 회원 엔티티가 분리(detach)되면 이후 코인 변경이 저장되지 않음
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Bet b SET b.status = :result, b.settledAt = :now WHERE b.id = :id AND b.status IN :from")
    int settle(@Param("id") Long id, @Param("result") BetStatus result, @Param("from") Collection<BetStatus> from, @Param("now") LocalDateTime now);

    //락 획득 실패(별도 트랜잭션): PENDING일 때만 ERROR
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Bet b SET b.status = com.mlbbroadcast.bet.BetStatus.ERROR WHERE b.id = :id AND b.status = com.mlbbroadcast.bet.BetStatus.PENDING")
    int markError(@Param("id") Long id);

    //보정 실패 횟수 증가(별도 트랜잭션). 정산되지 않은 Bet만
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Bet b SET b.retryCount = b.retryCount + 1 WHERE b.id = :id AND b.status IN (com.mlbbroadcast.bet.BetStatus.PENDING, com.mlbbroadcast.bet.BetStatus.ERROR)")
    int incrementRetryCount(@Param("id") Long id);

    //보정 한도 초과(별도 트랜잭션)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Bet b SET b.status = com.mlbbroadcast.bet.BetStatus.FAILED WHERE b.id = :id AND b.retryCount >= :limit AND b.status IN (com.mlbbroadcast.bet.BetStatus.PENDING, com.mlbbroadcast.bet.BetStatus.ERROR)")
    int markFailedIfExceeded(@Param("id") Long id, @Param("limit") int limit);
}
