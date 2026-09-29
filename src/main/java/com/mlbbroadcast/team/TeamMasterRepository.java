package com.mlbbroadcast.team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Map;


public interface TeamMasterRepository extends JpaRepository<TeamMaster, Long> {

    Long findByExternalId(int externalId);

    @Query
    Map<Integer, TeamMaster> findAllByIsActive(boolean isActive);
}
