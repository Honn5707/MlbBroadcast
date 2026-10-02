package com.mlbbroadcast.player.entity;

import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.team.TeamMaster;
import com.mlbbroadcast.team.TeamMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlayerService {
    private final MlbApiClient mlbApiClient;
    private final TeamMasterRepository teamMasterRepository;
    public void updatePlayerMaster(){

    }
}
