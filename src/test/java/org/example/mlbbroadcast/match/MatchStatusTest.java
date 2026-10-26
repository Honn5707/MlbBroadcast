package org.example.mlbbroadcast.match;

import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.enums.MatchStatusConverter;
import com.mlbbroadcast.common.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.mlbbroadcast.match.enums.MatchStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchStatusTest {

    @Test
    @DisplayName("[10-14] 종료 상태(FINAL, CANCELED)에서는 어떤 상태로도 전이할 수 없다")
    void terminalStates() {
        for (MatchStatus next : MatchStatus.values()) {
            assertThat(FINAL.canTransitionTo(next)).isFalse();
            assertThat(CANCELED.canTransitionTo(next)).isFalse();
        }
        assertThat(FINAL.isTerminal()).isTrue();
        assertThat(CANCELED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("문서의 허용 전이표")
    void allowedTransitions() {
        assertThat(SCHEDULED.canTransitionTo(LIVE)).isTrue();
        assertThat(SCHEDULED.canTransitionTo(DELAYED)).isTrue();
        assertThat(SCHEDULED.canTransitionTo(CANCELED)).isTrue();
        assertThat(DELAYED.canTransitionTo(LIVE)).isTrue();
        assertThat(DELAYED.canTransitionTo(MATCH_SUSPENDED_UNKNOWN)).isTrue();
        assertThat(LIVE.canTransitionTo(FINAL)).isTrue();
        assertThat(LIVE.canTransitionTo(DELAYED)).isTrue();
        assertThat(MATCH_SUSPENDED_UNKNOWN.canTransitionTo(LIVE)).isTrue();
        assertThat(MATCH_SUSPENDED_UNKNOWN.canTransitionTo(FINAL)).isTrue();
        assertThat(MATCH_SUSPENDED_UNKNOWN.canTransitionTo(CANCELED)).isTrue();
    }

    @Test
    @DisplayName("역행/표에 없는 전이는 거부한다 (LIVE -> SCHEDULED, LIVE -> CANCELED, SCHEDULED -> MATCH_SUSPENDED_UNKNOWN)")
    void rejectedTransitions() {
        assertThat(LIVE.canTransitionTo(SCHEDULED)).isFalse();
        assertThat(LIVE.canTransitionTo(CANCELED)).isFalse();
        assertThat(SCHEDULED.canTransitionTo(MATCH_SUSPENDED_UNKNOWN)).isFalse();
        assertThat(MATCH_SUSPENDED_UNKNOWN.canTransitionTo(DELAYED)).isFalse();
    }

    @Test
    @DisplayName("[문서 보완] 따라잡기/지연 중 종료를 위해 SCHEDULED -> FINAL, DELAYED -> FINAL 허용")
    void addedTransitions() {
        assertThat(SCHEDULED.canTransitionTo(FINAL)).isTrue();
        assertThat(DELAYED.canTransitionTo(FINAL)).isTrue();
    }

    @Test
    @DisplayName("엔티티에서 허용되지 않은 전이를 시도하면 예외")
    void entityRejects() {
        Matches match = Matches.builder().matchStatus(FINAL).scheduledStartedTime(LocalDateTime.now()).build();

        assertThatThrownBy(() -> match.changeStatus(LIVE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FINAL 확정 시 승리팀 계산 (홈 승 / 원정 승 / 동점 NONE)")
    void winSide() {
        Matches home = Matches.builder().matchStatus(LIVE).scheduledStartedTime(LocalDateTime.now()).build();
        home.finalizeResult(5, 3, LocalDateTime.now());
        Matches visit = Matches.builder().matchStatus(LIVE).scheduledStartedTime(LocalDateTime.now()).build();
        visit.finalizeResult(1, 2, LocalDateTime.now());
        Matches tie = Matches.builder().matchStatus(LIVE).scheduledStartedTime(LocalDateTime.now()).build();
        tie.finalizeResult(4, 4, LocalDateTime.now());

        assertThat(home.getWinSide()).isEqualTo(Side.HOME);
        assertThat(visit.getWinSide()).isEqualTo(Side.VISIT);
        assertThat(tie.getWinSide()).isEqualTo(Side.NONE);
        assertThat(home.getMatchStatus()).isEqualTo(FINAL);
        assertThat(home.getEndedTime()).isNotNull();
    }

    @Test
    @DisplayName("이전 상태값(BEFORE/PLAYED/FINISHED/SUSPENDED)도 읽을 수 있고, 저장은 새 이름으로 한다")
    void legacyConverter() {
        MatchStatusConverter converter = new MatchStatusConverter();

        assertThat(converter.convertToEntityAttribute("BEFORE")).isEqualTo(SCHEDULED);
        assertThat(converter.convertToEntityAttribute("PLAYED")).isEqualTo(LIVE);
        assertThat(converter.convertToEntityAttribute("FINISHED")).isEqualTo(FINAL);
        assertThat(converter.convertToEntityAttribute("SUSPENDED")).isEqualTo(MATCH_SUSPENDED_UNKNOWN);
        assertThat(converter.convertToEntityAttribute("DELAYED")).isEqualTo(DELAYED);
        assertThat(converter.convertToDatabaseColumn(SCHEDULED)).isEqualTo("SCHEDULED");
    }
}
