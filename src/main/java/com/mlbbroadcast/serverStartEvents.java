package com.mlbbroadcast;

import com.mlbbroadcast.match.MatchScheduler;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.player.PlayerService;
import com.mlbbroadcast.player.entity.PlayerMaster;
import com.mlbbroadcast.team.TeamScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class serverStartEvents {

    private final TeamScheduler teamScheduler;
    private final MatchScheduler matchScheduler;
    private final MatchesRepository matchesRepository;
    private final PlayerService playerService;

    @EventListener(ApplicationReadyEvent.class)
    public void startServerEvent(){
        teamScheduler.teamMasterUpdateScheduler();



        playerService.updatePlayerMaster();

        matchScheduler.receiveScheduledPolling();

        restoreSchedules();
    }


    public void restoreSchedules() {
        List<Matches> before = matchesRepository.findAllByMatchStatus(MatchStatus.BEFORE);
        before.forEach(matchScheduler::scheduleMatchStart);

        List<Matches> live = matchesRepository.findAllByMatchStatus(MatchStatus.PLAYED);
        live.forEach(match -> matchScheduler.startPollingForUpdateMatch(match.getId(), match.getGamePk()));
    }

}
