package com.mlbbroadcast.match.repositories;

import com.mlbbroadcast.match.entities.Matches;
import org.springframework.data.jpa.repository.JpaRepository;


public interface MatchesRepository extends JpaRepository<Matches, Long> {

}
