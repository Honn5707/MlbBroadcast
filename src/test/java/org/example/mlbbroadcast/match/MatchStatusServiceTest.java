package org.example.mlbbroadcast.match;

import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.match.service.MatchStatusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.mlbbroadcast.match.service.MatchStatusService.Outcome.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchStatusServiceTest {
    @Mock MatchesRepository matchesRepository;

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 12, 0);

    private Matches match(MatchStatus status) {
        Matches match = Matches.builder().matchStatus(status).scheduledStartedTime(NOW.minusHours(3)).build();
        when(matchesRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(match));
        return match;
    }

    @Test
    @DisplayName("FINAL 확정은 한 번만 반영되고, 두 번째 호출은 점수를 바꾸지 않는다 (FINAL 이후 불변)")
    void finalizeOnce() {
        Matches match = match(MatchStatus.LIVE);
        MatchStatusService service = new MatchStatusService(matchesRepository);

        assertThat(service.finalizeMatch(1L, 5, 3, NOW).outcome()).isEqualTo(CHANGED);
        assertThat(service.finalizeMatch(1L, 9, 9, NOW.plusHours(1)).outcome()).isEqualTo(UNCHANGED);

        assertThat(match.getHomeTeamScore()).isEqualTo(5);
        assertThat(match.getEndedTime()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("CANCELED 경기는 FINAL로 확정할 수 없다")
    void finalizeCanceledRejected() {
        Matches match = match(MatchStatus.CANCELED);

        assertThat(new MatchStatusService(matchesRepository).finalizeMatch(1L, 1, 0, NOW).outcome()).isEqualTo(REJECTED);
        assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.CANCELED);
    }

    @Test
    @DisplayName("[10-14] FINAL -> LIVE 전이는 거부된다")
    void finalToLiveRejected() {
        Matches match = match(MatchStatus.FINAL);

        assertThat(new MatchStatusService(matchesRepository).changeStatus(1L, MatchStatus.LIVE, NOW).outcome()).isEqualTo(REJECTED);
        assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.FINAL);
    }

    @Test
    @DisplayName("[락] 모든 수정 메서드는 락 조회(findByIdForUpdate)로만 경기를 읽는다")
    void allWritesUseLockedRead() {
        match(MatchStatus.SCHEDULED);
        MatchStatusService service = new MatchStatusService(matchesRepository);

        service.changeStatus(1L, MatchStatus.LIVE, NOW);
        service.finalizeMatch(1L, 1, 0, NOW);
        service.recordPollingPhase(1L, com.mlbbroadcast.match.polling.PollingPhase.STATUS);
        service.reschedule(1L, NOW);

        org.mockito.Mockito.verify(matchesRepository, org.mockito.Mockito.times(4)).findByIdForUpdate(1L);
        org.mockito.Mockito.verify(matchesRepository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("DELAYED 재관측 시 지연 시작 시각은 처음 값을 유지한다")
    void delayedSinceKept() {
        Matches match = match(MatchStatus.SCHEDULED);
        MatchStatusService service = new MatchStatusService(matchesRepository);

        assertThat(service.changeStatus(1L, MatchStatus.DELAYED, NOW).outcome()).isEqualTo(CHANGED);
        assertThat(service.changeStatus(1L, MatchStatus.DELAYED, NOW.plusHours(2)).outcome()).isEqualTo(UNCHANGED);

        assertThat(match.getDelayedSince()).isEqualTo(NOW);
    }
}
