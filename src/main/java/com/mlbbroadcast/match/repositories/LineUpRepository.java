package com.mlbbroadcast.match.repositories;

import com.mlbbroadcast.match.entities.LineUp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LineUpRepository extends JpaRepository<LineUp, Long> {

    //선수 동기화 후 마스터에 추가된 선수의 내부 id를 external_id로 재매핑
    @Modifying(clearAutomatically = true)
    @Query("UPDATE LineUp l SET l.playerId = (SELECT p.id FROM PlayerMaster p WHERE p.externalId = l.externalId) " +
            "WHERE l.playerId IS NULL AND EXISTS (SELECT 1 FROM PlayerMaster p2 WHERE p2.externalId = l.externalId)")
    int remapNullPlayerIds();
}
