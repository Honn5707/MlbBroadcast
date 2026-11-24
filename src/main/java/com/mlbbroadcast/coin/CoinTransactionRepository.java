package com.mlbbroadcast.coin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, Long> {

    Optional<CoinTransaction> findByBetIdAndPointReason(Long betId, PointReason pointReason);
}
