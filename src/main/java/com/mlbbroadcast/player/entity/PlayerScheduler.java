package com.mlbbroadcast.player.entity;


import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlayerScheduler {
    private final PlayerService playerService;
    @Scheduled(cron = "0 0 0 * * 1")
    public void updatePlayerMaster(){


    }
}
