package com.mlbbroadcast.bet.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BetSettlementRepository extends JpaRepository<BetSettlement, Long> {
    Optional<BetSettlement> findByMatchId(Long matchId);
}
