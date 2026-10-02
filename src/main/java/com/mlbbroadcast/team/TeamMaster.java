package com.mlbbroadcast.team;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "team_master")
@Getter

@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class TeamMaster {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "short_name", nullable = false)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(name = "division", nullable = false)
    private Division division;

    @Column(name = "external_id", nullable = false)
    private int externalId;

    @Column(name = "is_active")
    private boolean isActive;

    @Builder
    public TeamMaster(String fullName, String shortName, Division division, int externalId){
        this.fullName = fullName;
        this.shortName = shortName;
        this.division = division;
        this.externalId= externalId;
        isActive=true;
    }
}
