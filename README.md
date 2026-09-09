# MLB 승부예측 사이트 (가명)

## 소개

MLB API를 활용해 경기 진행 상황을 실시간 중계하고, 사용자는 원하는 팀에 베팅한다. 베팅한 팀이 승리하면 서비스 내 코인을 보상으로 획득한다.

## 주요 기능

### 실시간 경기 중계
- 경기 상태(프리게임/진행중/종료), 이닝, 득점, 주요 이벤트(안타/홈런/투수교체 등) 업데이트
- 경기별 채팅 시스템

### 최애 선수 시스템
- 최애 선수를 지정 가능 (최대 3명)
- 가장 최애가 많은 선수는 메인 페이지에 표시

### 베팅
- 경기 단위로 "승리 팀 맞히기" 형태
- 베팅은 경기 전, 경기 시작 후 최대 8이닝 전까지 가능. 단, 경기 시작 후 진행된 이닝에 따라 포인트 지급량 감소 (현재는 경기 시작 전에만 가능한 형태로 변경할지 고민 중)
- 최애 선수를 지정하여 해당 선수가 POG(Player Of Game)일 경우 추가 포인트 및 해당 경기에 대한 뱃지 획득
- POG는 경기 결과에 대한 프롬프트를 OpenAI로 분석하여 선정

### 정산 및 보상 (코인)
- 경기 종료 시점 기준으로 승/패 판정
- 적중 시 코인 지급, 실패 시 코인 차감

### 코인 시스템
- 코인으로 칭호, 프로필 아이콘, 마이페이지 내 다양한 치장 아이템 등을 구매 가능

## ERD

### Enum

| Enum | 값 |
|---|---|
| Division | AL_EAST, AL_CENTRAL, AL_WEST, NL_EAST, NL_CENTRAL, NL_WEST |
| PlayerPosition | PITCHER, CATCHER, FIRST_BASE, SECOND_BASE, THIRD_BASE, SHORTSTOP, LEFT_FIELD, CENTER_FIELD, RIGHT_FIELD, DESIGNATED_HITTER |
| Provider | GOOGLE, KAKAO, NAVER |
| MatchStatus | BEFORE, PLAYED, FINISHED |

### 다이어그램

```mermaid
erDiagram
    TEAM_MASTER ||--o{ PLAYER_MASTER : has
    TEAM_MASTER ||--o{ MATCHES : has
    TEAM_RECORD }o--|| TEAM_MASTER: has
    TEAM_RECORD }o--|| MATCHES : generates
    PLAYER_PITCHER_RECORD }o--|| PLAYER_MASTER : has 
    PLAYER_PITCHER_RECORD }o--|| MATCHES : generates 
    PLAYER_HITTER_RECORD }o--|| PLAYER_MASTER : has 
    PLAYER_HITTER_RECORD }o--|| MATCHES : generates 
    FAVORITE_PLAYER }o--|| PLAYER_MASTER : is_favorited
    FAVORITE_PLAYER }o--|| MEMBER : has
    BET }o--|| MATCHES : has
    BET }o--|| MEMBER : places
    COIN_TRANSACTION }o--|| MEMBER : has
    COIN_TRANSACTION }o--|| BET : settles_into
    MATCH_PLAYLOG }o--|| PLAYER_MASTER : has
    MATCH_PLAYLOG }o--|| MATCHES : has

    TEAM_MASTER {
        long id PK
        string name
        enum division
        long external_id
    }

    PLAYER_MASTER {
        long id PK
        long team_id FK
        enum position
        string name
        long external_id
    }

    MATCHES {
        long id PK
        long home_team_id FK
        long visit_team_id FK
        integer home_team_score
        integer visit_team_score
        enum winner
        enum match_status
        localdatetime start_time
        localdatetime end_time
        int season_year
        long external_id
    }

    TEAM_RECORD {
        long id PK
        long team_id FK
        long match_id FK
        double team_avg
        double team_obp
        double team_slg
        double team_ops
        double team_era
        double team_whip
    }

    PLAYER_PITCHER_RECORD {
        long id PK
        long player_id FK
        long match_id FK
        double player_ops
    }

    PLAYER_HITTER_RECORD {
        long id PK
        long player_id FK
        long match_id FK
        double player_avg
    }

    MEMBER {
        long id PK
        long provider_id
        enum provider_type
        string nickname
        localdatetime created_at
        long coin_remain
    }

    FAVORITE_PLAYER {
        long id PK
        long member_id FK
        long player_id FK
        localdatetime created_at
    }

    BET {
        long id PK
        long match_id FK
        long member_id FK
        integer amount
        enum bet_side
        enum status
        localdatetime created_at
        localdatetime settled_at
    }

    COIN_TRANSACTION {
        long id PK
        long member_id FK
        enum point_reason
        integer amount
        localdatetime created_at
        long bet_id FK "nullable"
    }

    MATCH_PLAYLOG {
        long id PK
        long player_id FK
        enum record_type
        int inning
        enum result
        string meta_data "JSON"
        boolean is_highlight
        long external_play_id
        long match_id FK
    }
```
