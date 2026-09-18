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

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "division", nullable = false)
    private Division division;

    @Column(name = "external_id", nullable = false)
    private int external_id;


    @Builder
    public TeamMaster(String name, Division division, int external_id){
        this.name = name;
        this.division = division;
        this.external_id = external_id;
    }
}
