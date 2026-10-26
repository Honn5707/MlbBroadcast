package com.mlbbroadcast.match.service;

import com.mlbbroadcast.match.repositories.LineUpRepository;
import com.mlbbroadcast.match.repositories.MatchPlaylogRepository;
import com.mlbbroadcast.player.repository.PlayerHitterRecordRepository;
import com.mlbbroadcast.player.repository.PlayerPitcherRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

//선수 동기화가 끝난 뒤, 마스터에 없어서 null로 저장된 선수 id를 external_id로 다시 채움 (docs/design/MatchSystem 4장)
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerIdRemapService {
    private final MatchPlaylogRepository matchPlaylogRepository;
    private final LineUpRepository lineUpRepository;
    private final PlayerHitterRecordRepository hitterRecordRepository;
    private final PlayerPitcherRecordRepository pitcherRecordRepository;

    @Transactional
    public void remapNullPlayerIds(){
        int batters = matchPlaylogRepository.remapNullBatterIds();
        int pitchers = matchPlaylogRepository.remapNullPitcherIds();
        int lineUps = lineUpRepository.remapNullPlayerIds();
        int hitterRecords = hitterRecordRepository.remapNullPlayerIds();
        int pitcherRecords = pitcherRecordRepository.remapNullPlayerIds();
        log.info("선수 id 재매핑 완료: batter={}, pitcher={}, lineUp={}, hitterRecord={}, pitcherRecord={}",
                batters, pitchers, lineUps, hitterRecords, pitcherRecords);
    }
}
