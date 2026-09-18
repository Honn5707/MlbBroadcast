package com.mlbbroadcast.match.repositories;

import com.mlbbroadcast.match.entities.PlayEvent;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PlayEventRepository extends JpaRepository<PlayEvent, Long> {


}
