package com.mlbbroadcast.match.entities;


import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.enums.MatchStatusConverter;
import com.mlbbroadcast.match.polling.PollingPhase;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.repository.Lock;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "Matches", uniqueConstraints = @UniqueConstraint(name = "uk_matches_game_pk", columnNames = "game_pk"))
@Getter

@NoArgsConstructor
public class Matches {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "home_team_id", nullable = false)
    private Long homeTeamId;
    @Column(name = "visit_team_id", nullable = false)
    private Long visitTeamId;

    @Column(name = "home_team_score", nullable = true)
    private Integer homeTeamScore;
    @Column(name = "visit_team_score", nullable = true)
    private Integer visitTeamScore;

    //NONE, HOME, VISIT
    @Enumerated(EnumType.STRING)
    @Column(name = "win_side", nullable = true)
    private Side winSide;

    //상태 전이는 changeStatus로만 변경 (MatchStatus 허용 전이 참고)
    @Convert(converter = MatchStatusConverter.class)
    @Column(name = "match_status", nullable = false, length = 32)
    private MatchStatus matchStatus;

    @Column(name = "scheduled_started_time", nullable = false)
    private LocalDateTime scheduledStartedTime;

    @Column(name = "real_started_time", nullable = true)
    private LocalDateTime realStartedTime;

    @Column(name = "ended_time", nullable = true)
    private LocalDateTime endedTime;

    @Column(name = "season_year", nullable = false)
    private String seasonYear;



    @Column(name = "game_pk", nullable = false)
    private int gamePk;

    //DELAYED를 처음 관측한 시각. 지연 한도(12h/6h) 계산용이며 재시작해도 이어서 계산하도록 저장
    @Column(name = "delayed_since", nullable = true)
    private LocalDateTime delayedSince;

    //현재 등록된 폴링 단계 (폴링 중이 아니면 null)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "polling_phase", nullable = true, length = 32)
    private PollingPhase pollingPhase;


    @Builder
    public Matches(Long homeTeamId, Long visitTeamId, MatchStatus matchStatus, LocalDateTime scheduledStartedTime,  String seasonYear, int gamePk){

        this.homeTeamId = homeTeamId;
        this.visitTeamId = visitTeamId;
        this.matchStatus = matchStatus;
        this.scheduledStartedTime = scheduledStartedTime;
        this.seasonYear = seasonYear;
        this.gamePk = gamePk;

    }


    //허용되지 않은 전이는 예외. 호출 측(MatchStatusService)에서 먼저 canTransitionTo로 확인하고 로그를 남김
    public void changeStatus(MatchStatus next){
        if(!matchStatus.canTransitionTo(next))
            throw new IllegalStateException("허용되지 않은 상태 전이: " + matchStatus + " -> " + next);
        this.matchStatus = next;
    }

    public void markDelayed(LocalDateTime now){
        if(this.delayedSince == null) this.delayedSince = now;
    }

    public void clearDelay(){this.delayedSince = null;}

    public void recordPollingPhase(PollingPhase pollingPhase){this.pollingPhase = pollingPhase;}

    public void reschedule(LocalDateTime scheduledStartedTime){this.scheduledStartedTime = scheduledStartedTime;}

    //FINAL 확정: 점수, 승리팀, 종료시각, 상태를 함께 기록
    public void finalizeResult(int homeTeamScore, int visitTeamScore, LocalDateTime endedTime){
        changeStatus(MatchStatus.FINAL);
        this.homeTeamScore = homeTeamScore;
        this.visitTeamScore = visitTeamScore;
        this.winSide = homeTeamScore > visitTeamScore ? Side.HOME
                : visitTeamScore > homeTeamScore ? Side.VISIT
                : Side.NONE;
        this.endedTime = endedTime;
        this.delayedSince = null;
    }

    public void matchStartTimeSet(LocalDateTime localDateTime){this.realStartedTime = localDateTime;}

    public void matchEndTimeSet(LocalDateTime localDateTime){this.endedTime = localDateTime;}

}
