package com.mlbbroadcast.player;

import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.seasonStats.SeasonStatsResponse;
import com.mlbbroadcast.external.mlbStatus.dto.seasonStats.SeasonStatsResponse.Split;
import com.mlbbroadcast.player.entity.PlayerHitterRecord;
import com.mlbbroadcast.player.entity.PlayerMaster;
import com.mlbbroadcast.player.entity.PlayerPitcherRecord;
import com.mlbbroadcast.player.repository.PlayerHitterRecordRepository;
import com.mlbbroadcast.player.repository.PlayerMasterRepository;
import com.mlbbroadcast.player.repository.PlayerPitcherRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

//선수 시즌 누적 스탯 저장 (docs/design/MatchSystem 7장)
//API가 주는 시즌 누적값으로 덮어쓰므로 여러 번 실행해도 결과가 같음. (player_external_id, season_year)당 한 행
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonStatService {
    private final MlbApiClient mlbApiClient;
    private final PlayerMasterRepository playerMasterRepository;
    private final PlayerHitterRecordRepository hitterRecordRepository;
    private final PlayerPitcherRecordRepository pitcherRecordRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    //외부 API(타자/투수 2회)는 트랜잭션 밖에서 호출하고, 덮어쓰기 저장만 트랜잭션으로 묶음
    public void updateSeasonStats(int season){
        Map<Integer, Split> hitting = onePerPlayer(mlbApiClient.getSeasonStats("hitting", season), "hitting");
        Map<Integer, Split> pitching = onePerPlayer(mlbApiClient.getSeasonStats("pitching", season), "pitching");
        transactionTemplate.executeWithoutResult(status -> persistSeasonStats(season, hitting, pitching));
    }

    private void persistSeasonStats(int season, Map<Integer, Split> hitting, Map<Integer, Split> pitching){
        String seasonYear = String.valueOf(season);
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        Map<Integer, Long> playerIds = playerMasterRepository.findAll().stream()
                .collect(Collectors.toMap(PlayerMaster::getExternalId, PlayerMaster::getId));

        Map<Integer, PlayerHitterRecord> hitters = hitterRecordRepository.findAllBySeasonYear(seasonYear).stream()
                .collect(Collectors.toMap(PlayerHitterRecord::getPlayerExternalId, Function.identity()));
        List<PlayerHitterRecord> newHitters = new ArrayList<>();
        hitting.forEach((externalId, split) -> {
            PlayerHitterRecord record = hitters.get(externalId);
            if(record == null){
                record = PlayerHitterRecord.builder().playerExternalId(externalId).playerId(playerIds.get(externalId)).seasonYear(seasonYear).build();
                newHitters.add(record);
            }
            SeasonStatsResponse.Stat stat = split.stat();
            record.overwrite(playerIds.get(externalId), teamId(split),
                    rate(stat.avg()), rate(stat.obp()), rate(stat.slg()), rate(stat.ops()), now);
        });
        hitterRecordRepository.saveAll(newHitters);

        Map<Integer, PlayerPitcherRecord> pitchers = pitcherRecordRepository.findAllBySeasonYear(seasonYear).stream()
                .collect(Collectors.toMap(PlayerPitcherRecord::getPlayerExternalId, Function.identity()));
        List<PlayerPitcherRecord> newPitchers = new ArrayList<>();
        pitching.forEach((externalId, split) -> {
            PlayerPitcherRecord record = pitchers.get(externalId);
            if(record == null){
                record = PlayerPitcherRecord.builder().playerExternalId(externalId).playerId(playerIds.get(externalId)).seasonYear(seasonYear).build();
                newPitchers.add(record);
            }
            record.overwrite(playerIds.get(externalId), teamId(split), rate(split.stat().era()), rate(split.stat().whip()), now);
        });
        pitcherRecordRepository.saveAll(newPitchers);

        log.info("시즌 스탯 저장 완료: season={}, hitter={}(신규 {}), pitcher={}(신규 {})",
                season, hitting.size(), newHitters.size(), pitching.size(), newPitchers.size());
    }

    //선수당 split 하나만 사용. 시즌 중 이적으로 팀별 split이 여러 개면 팀 정보가 없는(합산) split을 사용하고,
    //합산 split이 없으면 [미결: 이적 선수 행 분리 여부]이므로 저장하지 않고 로그만 남김
    private Map<Integer, Split> onePerPlayer(SeasonStatsResponse response, String group){
        if(response == null || response.stats() == null) return Map.of();
        Map<Integer, List<Split>> byPlayer = response.stats().stream()
                .filter(statGroup -> statGroup.splits() != null)
                .flatMap(statGroup -> statGroup.splits().stream())
                .filter(split -> split.player() != null && split.player().id() != null && split.stat() != null)
                .collect(Collectors.groupingBy(split -> split.player().id(), LinkedHashMap::new, Collectors.toList()));

        Map<Integer, Split> result = new LinkedHashMap<>();
        byPlayer.forEach((externalId, splits) -> {
            if(splits.size() == 1){
                result.put(externalId, splits.getFirst());
                return;
            }
            splits.stream().filter(split -> split.team() == null || split.team().id() == null).findFirst()
                    .ifPresentOrElse(total -> result.put(externalId, total),
                            () -> log.warn("이적 선수의 팀별 스탯만 있어 저장하지 않음([미결] 행 분리 여부): group={}, playerExternalId={}, splits={}",
                                    group, externalId, splits.size()));
        });
        return result;
    }

    private Integer teamId(Split split){
        return split.team() == null ? null : split.team().id();
    }

    //".285" -> 0.285, "-.--" / "*.**" / 빈 값 -> null
    static Double rate(String value){
        if(value == null || value.isBlank()) return null;
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
