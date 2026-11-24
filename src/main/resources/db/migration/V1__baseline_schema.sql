-- 기준 스키마 (엔티티 매핑 기준, MySQL 8.x / InnoDB)
-- 베팅 시스템의 정합성 제약(UNIQUE/인덱스)과 bet_settlement는 V2에서 추가
-- 현재 Flyway는 테스트(Testcontainers)에서만 실행됨. 운영 적용 시 기존 DB는 baseline-on-migrate로 V1을 건너뛸 것

create table members (
    id            bigint       not null auto_increment,
    provider_id   varchar(255) not null,
    provider_type enum ('GOOGLE','KAKAO','NAVER') not null,
    nickname      varchar(255) not null,
    created_at    datetime(6)  not null,
    coin_remain   integer      not null,
    primary key (id),
    constraint uk_members_provider_id unique (provider_id),
    constraint uk_members_nickname unique (nickname)
) engine=InnoDB;

create table matches (
    id                     bigint       not null auto_increment,
    home_team_id           bigint       not null,
    visit_team_id          bigint       not null,
    home_team_score        integer,
    visit_team_score       integer,
    win_side               enum ('HOME','NONE','VISIT'),
    match_status           varchar(32)  not null,
    scheduled_started_time datetime(6)  not null,
    real_started_time      datetime(6),
    ended_time             datetime(6),
    season_year            varchar(255) not null,
    game_pk                integer      not null,
    delayed_since          datetime(6),
    polling_phase          varchar(32),
    primary key (id),
    constraint uk_matches_game_pk unique (game_pk)
) engine=InnoDB;

create table bet (
    id          bigint      not null auto_increment,
    match_id    bigint      not null,
    member_id   bigint      not null,
    amount      integer     not null,
    bet_side    varchar(16) not null,
    status      varchar(16) not null,
    retry_count integer     not null,
    created_at  datetime(6) not null,
    settled_at  datetime(6),
    primary key (id)
) engine=InnoDB;

create table coin_transaction (
    id           bigint      not null auto_increment,
    member_id    bigint      not null,
    point_reason varchar(32) not null,
    amount       integer     not null,
    created_at   datetime(6) not null,
    bet_id       bigint,
    primary key (id)
) engine=InnoDB;

create table favorite_player (
    id         bigint      not null auto_increment,
    member_id  bigint      not null,
    player_id  bigint      not null,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table line_up (
    id             bigint not null auto_increment,
    match_id       bigint,
    team_id        bigint,
    player_id      bigint,
    external_id    integer,
    batting_order  integer,
    primary key (id)
) engine=InnoDB;

create table match_play_log (
    id                  bigint  not null auto_increment,
    match_id            bigint  not null,
    at_bat_index        integer not null,
    inning              integer not null,
    is_top_inning       bit     not null,
    batter_external_id  integer not null,
    pitcher_external_id integer not null,
    batter_id           bigint,
    pitcher_id          bigint,
    result_description  varchar(255),
    primary key (id),
    constraint uk_match_play_log_match_at_bat unique (match_id, at_bat_index)
) engine=InnoDB;

create index idx_match_play_log_batter_external_id on match_play_log (batter_external_id);
create index idx_match_play_log_pitcher_external_id on match_play_log (pitcher_external_id);

create table play_event (
    id                    bigint not null auto_increment,
    match_play_log_id     bigint not null,
    pitch_index           integer,
    start_speed           float(53),
    end_speed             float(53),
    description           varchar(255),
    event                 varchar(255),
    pitch_type_descriptio varchar(255),
    primary key (id)
) engine=InnoDB;

create index idx_play_event_match_play_log_id on play_event (match_play_log_id);

create table player_master (
    id            bigint       not null auto_increment,
    external_id   integer      not null,
    team_id       bigint       not null,
    name          varchar(255) not null,
    position      varchar(255) not null,
    jersey_number integer,
    primary key (id),
    constraint uk_player_master_external_id unique (external_id)
) engine=InnoDB;

create table player_hitter_record (
    id                 bigint       not null auto_increment,
    player_id          bigint,
    player_external_id integer      not null,
    team_external_id   integer,
    season_year        varchar(255) not null,
    player_avg         float(53),
    player_obp         float(53),
    player_slg         float(53),
    player_ops         float(53),
    updated_at         datetime(6)  not null,
    primary key (id),
    constraint uk_player_hitter_record_player_season unique (player_external_id, season_year)
) engine=InnoDB;

create table player_pitcher_record (
    id                 bigint       not null auto_increment,
    player_id          bigint,
    player_external_id integer      not null,
    team_external_id   integer,
    season_year        varchar(255) not null,
    player_era         float(53),
    player_whip        float(53),
    updated_at         datetime(6)  not null,
    primary key (id),
    constraint uk_player_pitcher_record_player_season unique (player_external_id, season_year)
) engine=InnoDB;

create table team_master (
    id          bigint       not null auto_increment,
    external_id integer      not null,
    full_name   varchar(255) not null,
    short_name  varchar(255) not null,
    division    enum ('AL_CENTRAL','AL_EAST','AL_WEST','NL_CENTRAL','NL_EAST','NL_WEST') not null,
    is_active   bit,
    primary key (id)
) engine=InnoDB;

create table team_record (
    id        bigint    not null auto_increment,
    match_id  bigint    not null,
    team_id   bigint    not null,
    team_avg  float(53) not null,
    team_obp  float(53) not null,
    team_slg  float(53) not null,
    team_ops  float(53) not null,
    team_era  float(53) not null,
    team_whip float(53) not null,
    primary key (id)
) engine=InnoDB;
