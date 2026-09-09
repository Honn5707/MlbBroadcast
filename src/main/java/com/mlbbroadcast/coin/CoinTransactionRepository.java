package com.mlbbroadcast.coin;

import org.springframework.data.jpa.repository.JpaRepository;


public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, Long> {

}
