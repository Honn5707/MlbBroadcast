package com.mlbbroadcast.match;


import com.mlbbroadcast.player.PlayerPosition;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "match_playlog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchPlaylog {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false)
    private PlayerPosition recordType;

    @Column(name = "inning", nullable = false)
    private int inning;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private PlayResult result;

    @Lob
    @Column(name = "meta_data", columnDefinition = "TEXT")
    private String metaData;

    @Column(name = "is_highlight", nullable = false)
    private boolean isHighlight;

    @Column(name = "external_play_id", nullable = false)
    private Long externalPlayId;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Builder
    public MatchPlaylog(Long playerId, PlayerPosition recordType, int inning, PlayResult result,
                        String metaData, boolean isHighlight, Long externalPlayId, Long matchId) {
        this.playerId = playerId;
        this.recordType = recordType;
        this.inning = inning;
        this.result = result;
        this.metaData = metaData;
        this.isHighlight = isHighlight;
        this.externalPlayId = externalPlayId;
        this.matchId = matchId;
    }
}
