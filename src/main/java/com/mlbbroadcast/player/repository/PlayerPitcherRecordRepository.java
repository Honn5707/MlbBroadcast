package com.mlbbroadcast.player.repository;

import com.mlbbroadcast.player.entity.PlayerPitcherRecord;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PlayerPitcherRecordRepository extends JpaRepository<PlayerPitcherRecord, Long> {

}
