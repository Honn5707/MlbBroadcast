package com.mlbbroadcast.player.repository;

import com.mlbbroadcast.player.entity.PlayerMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;


public interface PlayerMasterRepository extends JpaRepository<PlayerMaster, Long> {

    @Query
    Optional<PlayerMaster> findByExternalId(int externalId);
}
