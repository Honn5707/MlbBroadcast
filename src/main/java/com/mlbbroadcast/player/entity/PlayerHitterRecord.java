package com.mlbbroadcast.player.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//타자 시즌 누적 스탯. (player_external_id, season_year)당 한 행, API의 시즌 누적값으로 덮어씀 (docs/design/MatchSystem 7장)
@Entity
@Table(name = "player_hitter_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_player_hitter_record_player_season", columnNames = {"player_external_id", "season_year"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerHitterRecord {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "player_external_id", nullable = false)
    private int playerExternalId;

    //마스터에 없는 선수는 null (선수 동기화 후 재매핑 대상)
    @Column(name = "player_id", nullable = true)
    private Long playerId;

    @Column(name = "season_year", nullable = false)
    private String seasonYear;

    @Column(name = "team_external_id", nullable = true)
    private Integer teamExternalId;

    //계산 불가(타석 없음 등)면 null
    @Column(name = "player_avg", nullable = true)
    private Double playerAvg;

    @Column(name = "player_obp", nullable = true)
    private Double playerObp;

    @Column(name = "player_slg", nullable = true)
    private Double playerSlg;

    @Column(name = "player_ops", nullable = true)
    private Double playerOps;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public PlayerHitterRecord(int playerExternalId, Long playerId, String seasonYear) {
        this.playerExternalId = playerExternalId;
        this.playerId = playerId;
        this.seasonYear = seasonYear;
    }

    //시즌 누적값으로 덮어쓰기 (증분 합산 금지)
    public void overwrite(Long playerId, Integer teamExternalId, Double avg, Double obp, Double slg, Double ops, LocalDateTime updatedAt){
        if(playerId != null) this.playerId = playerId;
        this.teamExternalId = teamExternalId;
        this.playerAvg = avg;
        this.playerObp = obp;
        this.playerSlg = slg;
        this.playerOps = ops;
        this.updatedAt = updatedAt;
    }
}
