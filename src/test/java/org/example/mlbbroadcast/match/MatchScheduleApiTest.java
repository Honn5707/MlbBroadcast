package org.example.mlbbroadcast.match;

import com.mlbbroadcast.configuration.GlobalExceptionHandler;
import com.mlbbroadcast.match.MatchController;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.match.service.MatchQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//[10-13] 일정 조회 API
@WebMvcTest
@WithMockUser
@ContextConfiguration(classes = {MatchController.class, MatchQueryService.class, GlobalExceptionHandler.class})
class MatchScheduleApiTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MatchesRepository matchesRepository;

    @Test
    @DisplayName("경기 없는 날은 200 + 빈 배열")
    void emptyDay() throws Exception {
        when(matchesRepository.findAllByScheduledStartedTimeGreaterThanEqualAndScheduledStartedTimeLessThanOrderByScheduledStartedTimeAsc(any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/matches/schedule/2026-10-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("잘못된 날짜 형식은 400 INVALID_REQUEST")
    void invalidDate() throws Exception {
        mockMvc.perform(get("/api/matches/schedule/2026-13-40"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/matches/schedule/20261009"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("KST 하루(2026-10-09)는 UTC [10-08 15:00, 10-09 15:00) 범위로 조회한다 (경계 시각 포함 여부)")
    void kstRange() throws Exception {
        when(matchesRepository.findAllByScheduledStartedTimeGreaterThanEqualAndScheduledStartedTimeLessThanOrderByScheduledStartedTimeAsc(any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/matches/schedule/2026-10-09")).andExpect(status().isOk());

        verify(matchesRepository).findAllByScheduledStartedTimeGreaterThanEqualAndScheduledStartedTimeLessThanOrderByScheduledStartedTimeAsc(
                LocalDateTime.of(2026, 10, 8, 15, 0), LocalDateTime.of(2026, 10, 9, 15, 0));
    }

    @Test
    @DisplayName("응답: 내부 id 포함, 시작 시각은 KST ISO-8601, realStartedTime 미포함")
    void responseShape() throws Exception {
        Matches match = Matches.builder().homeTeamId(10L).visitTeamId(20L).matchStatus(MatchStatus.SCHEDULED)
                .scheduledStartedTime(LocalDateTime.of(2026, 10, 9, 10, 5)).seasonYear("2026").gamePk(777).build();
        ReflectionTestUtils.setField(match, "id", 3L);
        match.matchStartTimeSet(LocalDateTime.of(2026, 10, 9, 10, 7));
        when(matchesRepository.findAllByScheduledStartedTimeGreaterThanEqualAndScheduledStartedTimeLessThanOrderByScheduledStartedTimeAsc(any(), any()))
                .thenReturn(List.of(match));

        mockMvc.perform(get("/api/matches/schedule/2026-10-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].gamePk").value(777))
                .andExpect(jsonPath("$[0].matchStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].scheduledStartedTime").value("2026-10-09T19:05:00+09:00"))
                .andExpect(jsonPath("$[0].realStartedTime").doesNotExist())
                .andExpect(jsonPath("$[0].pollingPhase").doesNotExist());
    }
}
