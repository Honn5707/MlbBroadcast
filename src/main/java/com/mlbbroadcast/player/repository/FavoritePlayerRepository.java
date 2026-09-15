package com.mlbbroadcast.player.repository;

import com.mlbbroadcast.player.entity.FavoritePlayer;
import org.springframework.data.jpa.repository.JpaRepository;


public interface FavoritePlayerRepository extends JpaRepository<FavoritePlayer, Long> {

}
