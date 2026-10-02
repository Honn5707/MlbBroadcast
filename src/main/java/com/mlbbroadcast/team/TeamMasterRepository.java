package com.mlbbroadcast.team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

import java.util.List;
import java.util.Map;
import java.util.Set;


public interface TeamMasterRepository extends JpaRepository<TeamMaster, Long> {


    @Query("SELECT m FROM TeamMaster m WHERE m.isActive = :isActive")
    Set<TeamMaster> findAllByIsActive(@Param("isActive") boolean isActive);
}
