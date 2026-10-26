package org.example.mlbbroadcast.player;

import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.seasonStats.SeasonStatsResponse;
import com.mlbbroadcast.external.mlbStatus.dto.seasonStats.SeasonStatsResponse.*;
import com.mlbbroadcast.player.SeasonStatService;
import com.mlbbroadcast.player.entity.PlayerHitterRecord;
import com.mlbbroadcast.player.entity.PlayerMaster;
import com.mlbbroadcast.player.entity.PlayerPitcherRecord;
import com.mlbbroadcast.player.repository.PlayerHitterRecordRepository;
import com.mlbbroadcast.player.repository.PlayerMasterRepository;
import com.mlbbroadcast.player.repository.PlayerPitcherRecordRepository;
import org.example.mlbbroadcast.match.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeasonStatServiceTest {
    @Mock MlbApiClient mlbApiClient;
    @Mock PlayerMasterRepository playerMasterRepository;
    @Mock PlayerHitterRecordRepository hitterRepository;
    @Mock PlayerPitcherRecordRepository pitcherRepository;
    SeasonStatService service;
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    //저장소 흉내: saveAll로 저장된 행을 다음 조회에서 돌려줌
    final List<PlayerHitterRecord> hitterTable = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new SeasonStatService(mlbApiClient, playerMasterRepository, hitterRepository, pitcherRepository,
                new TransactionTemplate(txManager), new MutableClock(Instant.parse("2026-10-09T15:00:00Z")));
        PlayerMaster known = PlayerMaster.builder().teamId(1L).externalId(100).name("a").position("P").build();
        ReflectionTestUtils.setField(known, "id", 7L);
        when(playerMasterRepository.findAll()).thenReturn(List.of(known));
        when(hitterRepository.findAllBySeasonYear(anyString())).thenAnswer(invocation ->
                hitterTable.stream().filter(record -> record.getSeasonYear().equals(invocation.getArgument(0))).toList());
        when(hitterRepository.saveAll(anyList())).thenAnswer(invocation -> { hitterTable.addAll(invocation.getArgument(0)); return invocation.getArgument(0); });
        when(pitcherRepository.findAllBySeasonYear(anyString())).thenReturn(List.of());
        when(mlbApiClient.getSeasonStats(eq("pitching"), anyInt())).thenReturn(new SeasonStatsResponse(List.of()));
    }

    private Split hitter(int playerId, Integer teamId, String avg) {
        return new Split("2026", new Stat(avg, ".350", ".450", ".800", null, null), teamId == null ? null : new Ref(teamId), new Ref(playerId));
    }

    private void apiHitting(int season, Split... splits) {
        when(mlbApiClient.getSeasonStats("hitting", season)).thenReturn(new SeasonStatsResponse(List.of(new StatGroup(List.of(splits)))));
    }

    @Test
    @DisplayName("[10-11] 두 번 실행해도 결과가 같다 (덮어쓰기, 증분 합산 없음)")
    void idempotent() {
        apiHitting(2026, hitter(100, 147, ".285"));

        service.updateSeasonStats(2026);
        service.updateSeasonStats(2026);

        assertThat(hitterTable).hasSize(1);
        PlayerHitterRecord record = hitterTable.getFirst();
        assertThat(record.getPlayerAvg()).isEqualTo(0.285);
        assertThat(record.getPlayerOps()).isEqualTo(0.8);
        assertThat(record.getPlayerId()).isEqualTo(7L);
        assertThat(record.getTeamExternalId()).isEqualTo(147);
    }

    @Test
    @DisplayName("[10-11] 같은 선수라도 season_year가 다르면 새 행")
    void newRowPerSeason() {
        apiHitting(2025, hitter(100, 147, ".250"));
        apiHitting(2026, hitter(100, 147, ".285"));

        service.updateSeasonStats(2025);
        service.updateSeasonStats(2026);

        assertThat(hitterTable).extracting(PlayerHitterRecord::getSeasonYear).containsExactlyInAnyOrder("2025", "2026");
    }

    @Test
    @DisplayName("값이 바뀌면 기존 행을 시즌 누적값으로 덮어쓴다")
    void overwrite() {
        apiHitting(2026, hitter(100, 147, ".285"));
        service.updateSeasonStats(2026);
        apiHitting(2026, hitter(100, 147, ".301"));

        service.updateSeasonStats(2026);

        assertThat(hitterTable).hasSize(1);
        assertThat(hitterTable.getFirst().getPlayerAvg()).isEqualTo(0.301);
    }

    @Test
    @DisplayName("마스터에 없는 선수는 player_id null, external_id로 저장 (재매핑 대상)")
    void unknownPlayer() {
        apiHitting(2026, hitter(999, 147, ".200"));

        service.updateSeasonStats(2026);

        assertThat(hitterTable.getFirst().getPlayerId()).isNull();
        assertThat(hitterTable.getFirst().getPlayerExternalId()).isEqualTo(999);
    }

    @Test
    @DisplayName("계산 불가 값(\"-.--\", \"*.**\")은 null로 저장")
    void unparsable() {
        apiHitting(2026, hitter(100, 147, "-.--"));

        service.updateSeasonStats(2026);

        assertThat(hitterTable.getFirst().getPlayerAvg()).isNull();
    }

    @Test
    @DisplayName("이적 선수: 팀별 split과 합산 split이 함께 오면 합산 split 사용, 팀별 split만 있으면 저장하지 않음([미결])")
    void tradedPlayer() {
        apiHitting(2026,
                hitter(100, 147, ".200"), hitter(100, 121, ".300"), hitter(100, null, ".250"),
                hitter(200, 147, ".210"), hitter(200, 121, ".310"));

        service.updateSeasonStats(2026);

        assertThat(hitterTable).hasSize(1);
        assertThat(hitterTable.getFirst().getPlayerExternalId()).isEqualTo(100);
        assertThat(hitterTable.getFirst().getPlayerAvg()).isEqualTo(0.25);
        assertThat(hitterTable.getFirst().getTeamExternalId()).isNull();
    }

    @Test
    @DisplayName("투수 스탯(ERA, WHIP) 저장")
    void pitcher() {
        apiHitting(2026);
        when(mlbApiClient.getSeasonStats("pitching", 2026)).thenReturn(new SeasonStatsResponse(List.of(new StatGroup(List.of(
                new Split("2026", new Stat(null, null, null, null, "3.45", "1.12"), new Ref(147), new Ref(100)))))));

        service.updateSeasonStats(2026);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlayerPitcherRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(pitcherRepository).saveAll(captor.capture());
        assertThat(captor.getValue().getFirst().getPlayerEra()).isEqualTo(3.45);
        assertThat(captor.getValue().getFirst().getPlayerWhip()).isEqualTo(1.12);
    }

    @Test
    @DisplayName("[트랜잭션 경계] 타자/투수 API를 모두 호출한 뒤 트랜잭션 안에서 저장")
    void apiOutsideTransaction() {
        apiHitting(2026, hitter(100, 147, ".285"));

        service.updateSeasonStats(2026);

        InOrder order = inOrder(mlbApiClient, txManager, hitterRepository);
        order.verify(mlbApiClient).getSeasonStats("hitting", 2026);
        order.verify(mlbApiClient).getSeasonStats("pitching", 2026);
        order.verify(txManager).getTransaction(org.mockito.ArgumentMatchers.any());
        order.verify(hitterRepository).findAllBySeasonYear("2026");
        order.verify(txManager).commit(org.mockito.ArgumentMatchers.any());
    }

    private static String anyString() { return org.mockito.ArgumentMatchers.anyString(); }
    private static int anyInt() { return org.mockito.ArgumentMatchers.anyInt(); }
    private static <T> T eq(T value) { return org.mockito.ArgumentMatchers.eq(value); }
}
