package com.mlbbroadcast.match.Repositories;

import com.mlbbroadcast.match.MatchPlaylog;
import org.springframework.data.jpa.repository.JpaRepository;


public interface MatchPlaylogRepository extends JpaRepository<MatchPlaylog, Long> {

}
