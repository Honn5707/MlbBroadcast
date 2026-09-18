package com.mlbbroadcast.match.repositories;

import com.mlbbroadcast.match.entities.LineUp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineUpRepository extends JpaRepository<LineUp, Long> {
}
