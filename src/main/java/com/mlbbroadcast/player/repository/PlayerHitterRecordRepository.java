package com.mlbbroadcast.player.repository;

import com.mlbbroadcast.player.entity.PlayerHitterRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PlayerHitterRecordRepository extends JpaRepository<PlayerHitterRecord, Long> {

    List<PlayerHitterRecord> findAllBySeasonYear(String seasonYear);

    //선수 동기화 후 마스터에 추가된 선수의 내부 id를 external_id로 재매핑
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PlayerHitterRecord r SET r.playerId = (SELECT p.id FROM PlayerMaster p WHERE p.externalId = r.playerExternalId) " +
            "WHERE r.playerId IS NULL AND EXISTS (SELECT 1 FROM PlayerMaster p2 WHERE p2.externalId = r.playerExternalId)")
    int remapNullPlayerIds();
}
