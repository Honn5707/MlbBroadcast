package com.mlbbroadcast.match.repositories;

import com.mlbbroadcast.match.entities.MatchPlaylog;
import org.springframework.data.jpa.repository.JpaRepository;


public interface MatchPlaylogRepository extends JpaRepository<MatchPlaylog, Long> {

}
