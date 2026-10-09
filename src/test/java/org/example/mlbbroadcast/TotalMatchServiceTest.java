package org.example.mlbbroadcast;

import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.external.mlbStatus.MlbApiClient;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlay;
import com.mlbbroadcast.external.mlbStatus.dto.currentPlays.CurrentPlayResponse;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.GameData;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.GameStatusResponse;
import com.mlbbroadcast.external.mlbStatus.dto.gameStatus.Status;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.event.MatchEndedPollingEvent;
import com.mlbbroadcast.match.event.MatchStartedEvent;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.match.service.SaveMatchService;
import com.mlbbroadcast.match.service.TotalMatchService;
import com.mlbbroadcast.util.RedisUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TotalMatchServiceTest {
    @Mock
    MlbApiClient mlbApiClient;
    @Mock
    RedisUtilities redis;
    @Mock
    SimpMessagingTemplate messagingTemplate;
    @Mock
    MatchesRepository matchesRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    SaveMatchService saveMatchService;

    TotalMatchService totalMatchService;

    private static final Long MATCH_ID = 1L;
    private static final int GAME_PK = 777;
    private static final Duration CACHE_DURATION = Duration.ofHours(1);
    private static final String CURRENT_PLAY_KEY = "currentPlay:CurrentPlayIndex:" + MATCH_ID;

    @BeforeEach
    void setUp() {
        DefaultProperties properties = new DefaultProperties();
        properties.getCurrentPlay().setCurrentDataKeyIndex("CurrentPlayIndex:");
        properties.getCurrentPlay().setCurrentPlayCacheDuration(CACHE_DURATION);
        properties.getCurrentPlay().setGameStatusCheckInterval(Duration.ofMinutes(5));
        properties.getPlayInfo().setMatchStartPollingLimit(Duration.ofHours(12));
        totalMatchService = new TotalMatchService(mlbApiClient, properties, redis, messagingTemplate,
                matchesRepository, eventPublisher, saveMatchService);
    }

    private Matches match(LocalDateTime scheduledStart) {
        Matches match = Matches.builder()
                .homeTeamId(10L).visitTeamId(20L)
                .matchStatus(MatchStatus.BEFORE)
                .scheduledStartedTime(scheduledStart)
                .seasonYear("2026")
                .gamePk(GAME_PK)
                .build();
        ReflectionTestUtils.setField(match, "id", MATCH_ID);
        return match;
    }

    private Matches startingMatch() {
        return match(LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(10));
    }

    private GameStatusResponse gameStatus(String abstractGameState) {
        return new GameStatusResponse(new GameData(null, new Status(null, abstractGameState)));
    }

    private CurrentPlay currentPlay(Integer atBatIndex) {
        return new CurrentPlay(null, null, null, atBatIndex, null, null, null);
    }

    @Nested
    @DisplayName("checkMatchStart")
    class CheckMatchStart {

        @Test
        @DisplayName("경기가 Live가 되면 PLAYED 상태를 저장하고 중계 시작 이벤트를 발행한다")
        void live() {
            Matches match = startingMatch();
            when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
            when(mlbApiClient.getGameStatus(GAME_PK)).thenReturn(gameStatus("Live"));

            totalMatchService.checkMatchStart(MATCH_ID);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.PLAYED);
            assertThat(match.getRealStartedTime()).isNotNull();
            verify(matchesRepository).save(match);
            verify(eventPublisher).publishEvent(new MatchStartedEvent(MATCH_ID, GAME_PK));
        }

        @Test
        @DisplayName("라인업 저장이 실패해도 중계 시작 이벤트는 발행된다")
        void liveEvenIfLineUpFails() {
            when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.of(startingMatch()));
            when(mlbApiClient.getGameStatus(GAME_PK)).thenReturn(gameStatus("Live"));
            doThrow(new RuntimeException("player not found")).when(saveMatchService).saveBattingOrderLineUp(MATCH_ID);

            totalMatchService.checkMatchStart(MATCH_ID);

            verify(eventPublisher).publishEvent(new MatchStartedEvent(MATCH_ID, GAME_PK));
        }

        @Test
        @DisplayName("이미 끝난 경기(Final)면 FINISHED 상태를 저장하고 폴링 종료 이벤트를 발행한다")
        void finalState() {
            Matches match = startingMatch();
            when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
            when(mlbApiClient.getGameStatus(GAME_PK)).thenReturn(gameStatus("Final"));

            totalMatchService.checkMatchStart(MATCH_ID);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.FINISHED);
            assertThat(match.getEndedTime()).isNotNull();
            verify(saveMatchService).saveAtBat(MATCH_ID, GAME_PK);
            verify(matchesRepository).save(match);
            verify(eventPublisher).publishEvent(new MatchEndedPollingEvent(MATCH_ID));
        }

        @Test
        @DisplayName("경기를 찾을 수 없으면 예외 대신 폴링 종료 이벤트를 발행한다")
        void matchNotFound() {
            when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.empty());

            totalMatchService.checkMatchStart(MATCH_ID);

            verify(eventPublisher).publishEvent(new MatchEndedPollingEvent(MATCH_ID));
            verify(mlbApiClient, never()).getGameStatus(anyInt());
        }

        @Test
        @DisplayName("시작 예정 시간으로부터 제한 시간이 지나면 API를 호출하지 않고 폴링을 종료한다")
        void startPollingExpired() {
            when(matchesRepository.findById(MATCH_ID))
                    .thenReturn(Optional.of(match(LocalDateTime.now(ZoneId.of("UTC")).minusHours(13))));

            totalMatchService.checkMatchStart(MATCH_ID);

            verify(eventPublisher).publishEvent(new MatchEndedPollingEvent(MATCH_ID));
            verify(mlbApiClient, never()).getGameStatus(anyInt());
        }

        @Test
        @DisplayName("상태 응답이 비어 있으면 예외 없이 다음 폴링을 기다린다")
        void nullStatusResponse() {
            Matches match = startingMatch();
            when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
            when(mlbApiClient.getGameStatus(GAME_PK)).thenReturn(null);

            totalMatchService.checkMatchStart(MATCH_ID);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.BEFORE);
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("fetchCurrentPlayData")
    class FetchCurrentPlayData {

        @Test
        @DisplayName("새 타석 데이터면 타석 저장, 캐시 갱신, 웹소켓 전송 후 종료 확인은 하지 않는다")
        void newAtBat() {
            CurrentPlay response = currentPlay(5);
            when(mlbApiClient.getCurrentPlay(GAME_PK)).thenReturn(new CurrentPlayResponse(response));
            when(redis.load(CURRENT_PLAY_KEY, CurrentPlay.class)).thenReturn(currentPlay(4));

            totalMatchService.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            verify(saveMatchService).saveAtBat(MATCH_ID, GAME_PK);
            verify(redis).save(CURRENT_PLAY_KEY, response, CACHE_DURATION);
            verify(messagingTemplate).convertAndSend("/topic/games/" + MATCH_ID + "/current-plays", response);
            verify(mlbApiClient, never()).getGameStatus(anyInt());
        }

        @Test
        @DisplayName("변화 없이 체크 주기가 지나고 경기 상태가 Final이면 경기를 종료한다")
        void staleAndFinal() {
            Matches match = startingMatch();
            when(mlbApiClient.getCurrentPlay(GAME_PK)).thenReturn(new CurrentPlayResponse(currentPlay(5)));
            when(redis.load(CURRENT_PLAY_KEY, CurrentPlay.class)).thenReturn(currentPlay(5));
            // 마지막 갱신 후 301초 경과
            when(redis.expireDuration(CURRENT_PLAY_KEY)).thenReturn(CACHE_DURATION.toSeconds() - 301);
            when(mlbApiClient.getGameStatus(GAME_PK)).thenReturn(gameStatus("Final"));
            when(matchesRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));

            totalMatchService.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            assertThat(match.getMatchStatus()).isEqualTo(MatchStatus.FINISHED);
            verify(matchesRepository).save(match);
            verify(eventPublisher).publishEvent(new MatchEndedPollingEvent(MATCH_ID));
        }

        @Test
        @DisplayName("변화가 없어도 체크 주기 전이면 경기 상태 API를 호출하지 않는다")
        void notStaleYet() {
            when(mlbApiClient.getCurrentPlay(GAME_PK)).thenReturn(new CurrentPlayResponse(currentPlay(5)));
            when(redis.load(CURRENT_PLAY_KEY, CurrentPlay.class)).thenReturn(currentPlay(5));
            when(redis.expireDuration(CURRENT_PLAY_KEY)).thenReturn(CACHE_DURATION.toSeconds() - 30);

            totalMatchService.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            verify(mlbApiClient, never()).getGameStatus(anyInt());
            verify(saveMatchService, never()).saveAtBat(anyLong(), anyInt());
        }

        @Test
        @DisplayName("Final이 아니면 경기를 종료하지 않는다")
        void staleButLive() {
            when(mlbApiClient.getCurrentPlay(GAME_PK)).thenReturn(new CurrentPlayResponse(currentPlay(5)));
            when(redis.load(CURRENT_PLAY_KEY, CurrentPlay.class)).thenReturn(currentPlay(5));
            when(redis.expireDuration(CURRENT_PLAY_KEY)).thenReturn(-2L);
            when(mlbApiClient.getGameStatus(GAME_PK)).thenReturn(gameStatus("Live"));

            totalMatchService.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            verify(matchesRepository, never()).findById(anyLong());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("현재 타석 응답이 null이면 예외 없이 건너뛴다")
        void nullCurrentPlay() {
            when(mlbApiClient.getCurrentPlay(GAME_PK)).thenReturn(null);

            totalMatchService.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            verify(redis, never()).load(anyString(), eq(CurrentPlay.class));
        }

        @Test
        @DisplayName("캐시된 타석의 atBatIndex가 null이어도 NPE 없이 타석을 저장한다")
        void cachedAtBatIndexNull() {
            when(mlbApiClient.getCurrentPlay(GAME_PK)).thenReturn(new CurrentPlayResponse(currentPlay(5)));
            when(redis.load(CURRENT_PLAY_KEY, CurrentPlay.class)).thenReturn(currentPlay(null));

            totalMatchService.fetchCurrentPlayData(MATCH_ID, GAME_PK);

            verify(saveMatchService).saveAtBat(MATCH_ID, GAME_PK);
        }
    }

    @Test
    @DisplayName("updateScheduleListForMatch: 하루 저장이 실패해도 나머지 날짜는 계속 저장한다")
    void scheduleContinuesOnFailure() {
        LocalDate today = LocalDate.of(2026, 10, 7);
        doThrow(new RuntimeException("api error")).when(saveMatchService).saveScheduledMatches(today.plusDays(3));

        totalMatchService.updateScheduleListForMatch(today);

        verify(saveMatchService, times(14)).saveScheduledMatches(any(LocalDate.class));
    }
}
