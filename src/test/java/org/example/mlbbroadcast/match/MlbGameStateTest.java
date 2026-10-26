package org.example.mlbbroadcast.match;

import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.GameData;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.GameStatusResponse;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.LiveData;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.Status;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.service.MlbGameState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MlbGameStateTest {

    private GameStatusResponse response(String abstractState, String detailedState) {
        return new GameStatusResponse(new GameData(null, new Status(null, abstractState, detailedState)), null);
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @CsvSource(nullValues = "null", value = {
            "Preview, Scheduled, SCHEDULED",
            "Preview, Pre-Game, SCHEDULED",
            "Preview, Delayed Start, DELAYED",
            "Live, In Progress, LIVE",
            "Live, Delayed, DELAYED",
            "Live, Suspended, DELAYED",
            "Final, Final, FINAL",
            "Final, Game Over, FINAL",
            "Final, Postponed, CANCELED",
            "Final, Cancelled, CANCELED",
            "Live, null, LIVE"
    })
    @DisplayName("MLB status(abstractGameState + detailedState) -> 내부 상태")
    void mapping(String abstractState, String detailedState, MatchStatus expected) {
        assertThat(MlbGameState.from(response(abstractState, detailedState)).status()).isEqualTo(expected);
    }

    @Test
    @DisplayName("응답/상태가 비어 있거나 알 수 없는 상태면 null")
    void unknown() {
        assertThat(MlbGameState.from(null)).isNull();
        assertThat(MlbGameState.from(new GameStatusResponse(null, null))).isNull();
        assertThat(MlbGameState.from(response(null, null))).isNull();
        assertThat(MlbGameState.from(response("Weird", null))).isNull();
    }

    @Test
    @DisplayName("linescore의 홈/원정 득점을 읽는다")
    void score() {
        LiveData liveData = new LiveData(new LiveData.Linescore(new LiveData.Teams(new LiveData.TeamScore(5), new LiveData.TeamScore(3))));
        MlbGameState state = MlbGameState.from(new GameStatusResponse(new GameData(null, new Status(null, "Final", "Final")), liveData));

        assertThat(state.homeRuns()).isEqualTo(5);
        assertThat(state.awayRuns()).isEqualTo(3);
        assertThat(state.hasScore()).isTrue();
        assertThat(MlbGameState.from(response("Final", "Final")).hasScore()).isFalse();
    }
}
