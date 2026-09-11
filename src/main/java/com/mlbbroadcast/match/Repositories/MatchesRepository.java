package com.mlbbroadcast.match.Repositories;

import com.mlbbroadcast.match.Matches;
import org.springframework.data.jpa.repository.JpaRepository;


public interface MatchesRepository extends JpaRepository<Matches, Long> {

}
