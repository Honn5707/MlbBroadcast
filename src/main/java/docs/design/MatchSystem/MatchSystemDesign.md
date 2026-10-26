# 매치 시스템 설계 (docs/design/MatchSystem/MatchSystemDesign.md)

> 상태: 구현중 / 작성일: 2026-10-09 / 최종 수정: 2026-10-09
> `[미결]` = 아직 결정되지 않은 항목. 구현하지 말고 질문할 것.
> `[확인 필요]` = 문서에 없던 부분을 구현하면서 정한 항목. 확정되면 태그를 지운다.

---

## 1. 목적과 범위

### 책임지는 것
- MLB API 기반 팀/선수/일정 동기화
- 경기 실시간 폴링, 타석(MatchPlayLog)·투구(PlayEvent) 저장
- 경기 종료(FINAL) 확정 및 점수/승리팀 저장
- 선수 시즌 누적 스탯 저장 (조회 API 없음, 저장만)
- 실시간 중계 전송(WebSocket/STOMP), 일정 조회 API

### 책임지지 않는 것 (이번 범위 밖)
- 베팅/코인 정산 (별도 시스템. 매치 시스템은 아래 "계약"만 제공)
- 선수 조회 API / 팀 조회 API `[미결]` — 구현하지 말 것

### 베팅 시스템과의 계약
매치 시스템은 스프링 이벤트로 다음을 발행한다.
- `MatchFinalizedEvent(matchId)`: FINAL 확정 후 (승리팀·점수 저장 커밋 완료 이후). 경기당 1회
- `MatchCanceledEvent(matchId)`: 취소/연기 확정 시
- `MatchSuspendedUnknownEvent(matchId)`: 한도 초과로 상태 불명 처리 시

### 베팅 시작을 위한 최소 완료 조건
- FINAL 경기는 승리팀·점수·종료시각이 저장된다
- 재시작·중복 호출·중복 폴링에도 결과가 같다
- 취소/연기/상태불명 경기를 구분할 수 있다

---

## 2. 전제와 한계

- **단일 인스턴스 전제.** 수집(폴링/배치)과 API가 한 프로세스에 있다.
- 락·중복 폴링 방어는 `PollingRegistry`, `JobLock` 인터페이스 뒤에 두고, 구현체는 메모리 기반(`InMemoryPollingRegistry`: synchronized, `InMemoryJobLock`: ReentrantLock)으로 작성한다.
- 폴링 상태는 메모리뿐 아니라 DB(`matches.polling_phase`: WAITING_START / STATUS / LIVE_PLAY / null)에도 기록한다. 재시작 복구는 경기 상태(SCHEDULED/LIVE/DELAYED) 기준으로 하고, 이 컬럼은 기록용이다. `[확인 필요]`
- 메모리에만 있는 것 (서버 재시작 시 유실): 10초 폴링 캐시, 재시도 예약, 종료 경기 구독 해제 예약.
- **다중화 시 깨지는 지점 (3단계 개선 과제)**
    - 스케줄러/일일 배치 중복 실행 → 분산 락(ShedLock 등)
    - 경기 폴링 중복 → Redis 분산 락 또는 DB 선점
    - 폴링 캐시 메모리 → Redis
    - 웹소켓 내장 SimpleBroker → 외부 브로커(Redis Pub/Sub 또는 RabbitMQ STOMP 릴레이)
    - 웹소켓 세션/구독 추적(`WebSocketSessionRegistry`, `MatchSubscriptionTracker`) → 인스턴스별로 나뉨
- 다중화 전환 시 "중복 폴링 재현 → 분산 락 적용 → 전후 비교"를 `experiments.md`에 기록한다.

---

## 3. 현황 (as-is, 설계 작성 시점)

| 주기/시점 | 동작 |
|---|---|
| 매년 3월 / 서버 구동 | 팀 조회, 없는 팀 저장 |
| 매주 월요일 00시 / 서버 구동 | 선수 조회, 신규 선수 저장, 팀 변경 반영 (※ 작성 당시 "매일"로 적혀 있었으나 실제 코드는 매주. 설계에 맞춰 매일로 변경함) |
| 매일 00시(UTC) / 서버 구동 | 오늘~13일 후(14일치) 일정 조회, 없는 경기 저장 후 스케줄러 등록 (※ 작성 당시 "5일 후"로 적혀 있었으나 실제 코드는 14일) |
| 서버 구동 | restore 로직으로 스케줄 재등록 (동기화 처리로 제거 선행 방지) |
| 경기 시작 시각 | 1분 반복 폴링으로 status 조회 |
| LIVE 진입 | 수비/라인업 1회 저장 → 10초 폴링(fetchCurrentPlayData)으로 이관 |
| 10초 폴링 | 현재 데이터 캐시, 동일하면 저장 X, 타석 변경 시 직전 타석까지 저장 (※ "이닝 변경 시 수비 저장"은 코드에 없음. `updateInning`은 호출되지 않음) |
| 5분간 타석 갱신 없음 | MLB API로 status 확인, FINAL이면 종료 + atBatIndex 기준 저장 |

### 알려진 구멍 (→ 처리 현황)
- 경기 종료(FINAL) 시 점수/승리팀 저장 미구현 → **구현 완료** (6장 FINAL 확정 저장)
- 선수 기록(player_hitter_record / pitcher_record) 미저장 → **구현 완료** (7장, 시즌 단위)
- 외부 ID → 내부 ID 매핑 미완성, null 재매핑 정책 없음 → **구현 완료** (4장)
- 인덱스/UNIQUE 미선언 → **구현 완료** (6장 멱등성)
- 조회 API 없음 → 일정 조회 **구현 완료**, 스냅샷 `[미결]`

---

## 4. 데이터 소스와 매핑

### 사용 MLB API 엔드포인트 (base: `https://statsapi.mlb.com/api`)
| 용도 | 엔드포인트 | 재시도 정책 적용 |
|---|---|---|
| 일정 | `GET /v1/schedule?sportId=1&date={date}` | O (날짜별) |
| 경기 상태 + 점수 | `GET /v1.1/game/{gamePk}/feed/live?fields=gameData,status,abstractGameState,detailedState,...,liveData,linescore,teams,home,away,runs` | X (status 폴링 자체가 재시도) |
| 현재 타석 | `GET /v1/game/{gamePk}/playByPlay?fields=currentPlay,...` | X (10초 폴링) |
| 전체 타석 | `GET /v1/game/{gamePk}/playByPlay?fields=allPlays,...` | X (폴링/FINAL 확정 흐름에서 재시도) |
| 라인업 | `GET /v1/game/{gamePk}/boxscore?fields=...` | O |
| 수비 위치 | `GET /v1/game/{gamePk}/linescore?fields=defense,...` | O |
| 팀 | `GET /v1/teams?sportId=1` | O |
| 선수(로스터) | `GET /v1/teams/{teamId}/roster?rosterType=40Man` | O (일시 장애 시 작업 전체 재예약) |
| 시즌 스탯 | `GET /v1/stats?stats=season&group={hitting\|pitching}&season={season}&sportId=1&playerPool=ALL` `[확인 필요: 실제 응답 미검증]` | O |

### 매핑 규칙
- `external_id` → 내부 id. 마스터에 없으면 `*_id`는 null, `*_external_id`는 항상 보존한다.
    - 적용 대상: `match_play_log`(batter/pitcher), `line_up`(player), `player_hitter_record`/`player_pitcher_record`(player)
    - 라인업의 팀은 선수의 현재 소속이 아니라 경기의 홈/원정 팀 기준으로 저장한다. `[확인 필요]`
- **null 재매핑**: 매일 00시 선수 동기화가 끝난 뒤, 위 대상 테이블에서 `*_id IS NULL`인 행을 external_id로 재매핑한다. (`batter_external_id`, `pitcher_external_id` 인덱스 선언)
- 팀 저장 시 `isActive`를 확인·정정한다.
    - API에 존재하는 팀 → active = true (비활성이었다가 다시 나타난 팀도 재활성화, 중복 저장하지 않음)
    - API에서 사라진 팀 → active = false (기존 경기/기록 데이터는 그대로 유지, 삭제하지 않음)
    - 팀 API 응답이 비어 있으면 전 팀 비활성화를 막기 위해 아무것도 바꾸지 않는다. `[확인 필요]`

---

## 5. 경기 상태 전이

### 상태
`SCHEDULED`, `LIVE`, `DELAYED`, `FINAL`, `CANCELED`(연기 포함), `MATCH_SUSPENDED_UNKNOWN`

### MLB status → 내부 상태 `[확인 필요]`
`abstractGameState`(Preview/Live/Final)를 기본으로 하고, `detailedState`로 지연/취소를 구분한다.

| 조건 | 내부 상태 |
|---|---|
| detailedState에 Postponed / Cancelled 포함 | CANCELED |
| detailedState에 Delayed / Suspended 포함 | DELAYED (Suspended는 지연 한도 초과 시 상태 불명으로 처리) |
| abstractGameState = Preview | SCHEDULED |
| abstractGameState = Live | LIVE |
| abstractGameState = Final | FINAL |
| 그 외 / 응답 없음 | 상태 변경 없음, 다음 폴링에서 재확인 |

### 허용 전이
| 현재 | 허용 다음 상태 |
|---|---|
| SCHEDULED | LIVE, DELAYED, CANCELED, **FINAL** |
| DELAYED | LIVE, CANCELED, MATCH_SUSPENDED_UNKNOWN, **FINAL** |
| LIVE | FINAL, DELAYED, MATCH_SUSPENDED_UNKNOWN |
| MATCH_SUSPENDED_UNKNOWN | LIVE, FINAL, CANCELED (다음날 일정 동기화에서 재확인) |
| FINAL, CANCELED | 없음 (종료 상태) |

- **SCHEDULED → FINAL, DELAYED → FINAL 추가**: 6장 "서버 다운 중 끝난 경기 따라잡기"(LIVE를 관측하지 못하고 FINAL)와 5장 "지연 중 FINAL → 종료 로직"(콜드게임 등)을 위해 필요. `[확인 필요]`
- 역행 금지. 허용되지 않은 전이는 코드에서 거부하고 로그를 남긴다. (`MatchStatusService`가 판단, 엔티티 `changeStatus`도 예외로 방어)
    - 예: LIVE 중 CANCELED 응답 → 거부, 폴링 유지
- FINAL 판정 기준: **MLB API의 status가 최종 판단**이다. 5분 무갱신은 "상태 재확인 트리거"일 뿐 종료 판정이 아니다.

### 지연(DELAYED) 처리
- 지연 시간은 DELAYED를 **처음 관측한 시각**(`matches.delayed_since`, DB 저장 → 재시작해도 이어서 계산)부터 잰다. LIVE로 복귀하면 초기화되어 다음 지연은 새로 센다. `[확인 필요]`
- 시작 전 지연과 경기 중 지연은 `real_started_time` 유무로 구분한다.
- 시작 시각 이후 DELAYED가 나오면 LIVE 또는 CANCELED가 나올 때까지 status를 반복 폴링(60초)한다. 최대 **12시간**.
- 12시간 초과 시 `MATCH_SUSPENDED_UNKNOWN`으로 전환, 폴링 종료, 이벤트 발행. 다음날 일정 동기화에서 같은 gamePk를 만나면 시작 시각을 갱신하고 폴링을 재등록한다.
- LIVE 중 5분 무갱신 + status가 DELAYED이면 타석 폴링을 중단하고 status만 반복 폴링(60초 `[확인 필요]`)한다. 최대 **6시간**.
    - LIVE로 복귀 → 현재 타석 저장 후 타석 폴링 재개 (라인업은 다시 저장하지 않음)
    - FINAL → 종료 로직
    - 6시간 초과 → `MATCH_SUSPENDED_UNKNOWN` 전환
- 시작 시각 후 12시간이 지나도 Preview(지연 아님)이면 폴링만 종료하고 상태는 SCHEDULED로 둔다. (전이표상 SCHEDULED → MATCH_SUSPENDED_UNKNOWN 불가) `[확인 필요]`
- 설정: `default-properties.play-info.delay-before-start-limit: 12h`, `delay-in-game-limit: 6h`, `match-start-polling-limit: 12h`

---

## 6. 수집·저장 정책

### 트랭잭션 최소화 
- 트랜잭션을 메서드 내부에 

### 멱등성
- `matches.game_pk` UNIQUE (`uk_matches_game_pk`)
- `match_play_log (match_id, at_bat_index)` UNIQUE (`uk_match_play_log_match_at_bat`) → 충돌 시 skip (이미 저장된 타석은 덮어쓰지 않음)
    - 구현: 저장 전 이미 저장된 `at_bat_index` 집합과 비교해 걸러낸다. 동시 저장으로 실제 충돌이 나면 예외 후 다음 폴링에서 다시 걸러진다. (트랜잭션 안에서 DB 예외를 삼키면 커밋이 실패하므로) `[확인 필요]`
    - 진행 중인(미완료, `about.isComplete=false`) 타석은 저장하지 않는다. 저장하면 이후 결과가 갱신되지 않기 때문.
- `play_event (match_play_log_id)` 인덱스

### 중복 폴링 방어
- gamePk당 폴링 작업은 하나만 존재한다. `PollingRegistry`에 gamePk 키로 등록 후 실행, 이미 있으면 등록 거부.
- 같은 경기의 단계 전환(시작 대기 → status 폴링 → 타석 폴링)은 `replace`로 이전 작업을 취소하고 교체한다.
- 스케줄 재등록(구동 + 복구 + 일일 배치)이 겹쳐도 위 규칙으로 방어한다.
- 복구 로직의 동기화 처리는 "등록/제거 레이스 방지"를 위한 JVM 락이다 (단일 인스턴스 전제).
- 일정 동기화·선수 동기화·스탯 배치는 `JobLock`으로 중복 실행(서버 구동 실행 + cron)을 막는다.

### FINAL 확정 저장
1. 마지막 타석을 `atBatIndex` 기준으로 저장
2. `winner`, 최종 점수, `end_time`, status=FINAL을 **한 트랜잭션**으로 저장 (점수는 feed/live의 `liveData.linescore.teams.{home,away}.runs`)
3. 커밋 후 `MatchFinalizedEvent` 발행
- **FINAL 이후 불변.** 점수 정정 재검증은 하지 않는다. 이미 FINAL이면 변경·이벤트 없음.
- 동점이면 `win_side = NONE`. `[확인 필요]`
- FINAL 응답에 점수가 없거나 1번 타석 저장이 실패하면 확정을 보류하고 다음 폴링에서 재시도한다. (타석이 빠진 채 확정되면 이후 보완할 수 없으므로) `[확인 필요]`

### 서버 다운 중 끝난 경기 따라잡기
- 구동 시 SCHEDULED/LIVE/DELAYED인 경기를 다시 등록한다. `시작 시각 < 현재`이면 즉시 status를 조회한다.
    - FINAL이면 전체 타석을 `atBatIndex` 기준으로 backfill 후 FINAL 확정 저장
    - LIVE이면 마지막 저장 타석 이후부터 이어서 폴링 (갭 구간은 backfill)
    - DELAYED이면 5장의 지연 처리 규칙 적용
- MATCH_SUSPENDED_UNKNOWN은 구동 시 복구하지 않고 다음 일정 동기화에서 재등록한다.
- 구동 작업(팀 → 선수 → 일정 → 복구)은 단계별로 예외를 격리해, 외부 API 장애가 있어도 경기 복구는 진행한다. `[확인 필요]`

### 10초 폴링 캐시
- 저장소: 메모리(경기별 마지막 타석 데이터 + 마지막 변경 시각). 서버 재시작 시 유실되어도 된다.
- 재시작 후 첫 응답은 DB에 저장된 `at_bat_index`와 비교해 갭이 있으면 backfill한다.

### 외부 API 재시도 (현재 타석 조회·status 폴링 제외)
- 재시도 대상: 타임아웃/연결 오류, 5xx, 429 (429는 `Retry-After`(초)가 백오프보다 길면 그 값을 따름)
- 즉시 실패: 4xx (429 제외), 외부 API가 아닌 예외(DB 오류 등)
- **방식: 다음 실행 예약.** 스레드를 붙잡고 대기하지 않고 `TaskScheduler`에 다음 시도를 예약한다. (스케줄러 스레드가 다른 경기 폴링을 막지 않도록)
- 간격: 5초 시작, 지수 백오프(5s → 10s → 20s …), 상한 60초
- 한도: 최대 횟수. 현재 **5회(임시값)** `[미결: 횟수/시간 수치]`
- **실패 상태 기록: 서버 로그.** 일시 실패·재시도 예약은 WARN, 즉시 실패·한도 초과는 ERROR("다음 정기 배치 또는 수동 재처리 필요").
- 한도 초과 시 다음 정기 배치 또는 수동 재처리로 복구. 무한 재시도하지 않는다.
- 설정: `default-properties.mlb-api-retry.initial-delay / max-delay / max-attempts`

---

## 7. 선수 시즌 스탯

- **시즌 누적 스탯을 저장한다** (경기 단위 아님). 이번 범위는 저장까지이며 조회 API는 없다.
- 실행: 매일 `stats-batch-cron`(현재 UTC 15:00 = KST 00:00, 임시) `[미결: 일일 마감 시각]`
    - "당일 모든 경기 종료 시 실행" 트리거는 아직 구현하지 않았다. 덮어쓰기 방식이라 실행 시각은 최신성에만 영향이 있다.
- **덮어쓰기 방식**: API가 주는 시즌 누적값으로 덮어쓴다 (증분 합산 금지). 재실행해도 같은 결과여야 한다.
- 키: `(player_external_id, season_year)` UNIQUE. 있으면 수정, 없으면 생성, `season_year`가 다르면 새 행.
- 저장 항목
    - 타자 `player_hitter_record`: avg, obp, slg, ops
    - 투수 `player_pitcher_record`: era, whip
    - 공통: player_external_id, player_id(null 허용), season_year, team_external_id, updated_at
    - 계산 불가 값(`-.--` 등)은 null
- 마스터에 없는 선수는 `player_id`를 null로 두고 external_id로 구분. 이후 4장의 재매핑 대상.
- 시즌 중 팀 이적 시 팀별 행 분리 여부: `[미결]`
    - 현재 동작: 팀별 split이 여러 개면 팀 정보가 없는 합산 split을 사용, 합산 split이 없으면 저장하지 않고 WARN 로그.
- 시즌 = 실행 시점의 UTC 연도.

---

## 8. API 명세

### 8-1. 일정 조회 `GET /api/matches/schedule/{date}`
- `{date}`는 **KST 기준 날짜**(예: `2026-10-09`)로 클라이언트가 전송한다.
- 서버는 KST 하루 범위를 UTC 범위로 변환해 `scheduled_started_time` 기준으로 조회한다. (시차 때문에 KST 하루에 미국 현지 날짜가 두 개 걸칠 수 있어, 날짜 문자열 변환이 아니라 시각 범위로 조회한다)
    - 예: `2026-10-09` → UTC `[2026-10-08T15:00, 2026-10-09T15:00)`
    - 변환 방식: 서버 내부 계산은 UTC 범위 기준, 필요 시 `America/New_York` 변환 `[미결: 현지 날짜 기준이 필요한 곳이 있는지]`
- 시작 시각은 DB에 UTC 저장, 응답은 KST ISO-8601 (예: `2026-10-09T19:05:00+09:00`).
- 응답 DTO: matches 엔티티에서 `realStartedTime`과 내부 관리 필드(`delayedSince`, `pollingPhase`)를 제외하고 **내부 `id`를 포함**한다. (id는 8-2의 matchId로 사용)
    - 필드: id, gamePk, homeTeamId, visitTeamId, homeTeamScore, visitTeamScore, winSide, matchStatus, scheduledStartedTime, endedTime, seasonYear
- 경기 없는 날: 200 + 빈 배열. 날짜 형식 오류: 400 `INVALID_REQUEST`.

### 8-2. 실시간 중계 (STOMP)
- 구독 목적지: `/topic/matches/{matchId}` (matchId = 내부 id)
- 연결 주체는 클라이언트다. 서버는 CONNECT 요청을 받아 처리한다.
- 인증/목적지 제한은 JWT 문서의 STOMP 정책을 따른다 (CONNECT에서 토큰 검증, `/topic` 클라이언트 SEND 금지).
- 재접속 대비: **현재 상태 스냅샷 조회 REST** `GET /api/matches/{matchId}`를 별도로 둔다 `[미결: 스냅샷 응답 항목]`.
- 구독 검사 (`MatchSubscriptionInterceptor`)
    - 존재하지 않는 matchId(숫자가 아닌 값 포함): 구독 거절 (`MATCH_NOT_FOUND`, STOMP ERROR 프레임)
    - FINAL 경기: 종료 후 10분 이내는 구독 허용, 이후는 거절 (`MATCH_ENDED`)
    - CANCELED 경기: 라이브 데이터가 없으므로 거절 (`MATCH_ENDED`) `[확인 필요]`
- **종료된 경기 구독 해제**: FINAL/CANCELED 확정 후 **10분 뒤** 해당 경기 구독을 해제한다. (`ended-match-disconnect-delay: 10m`)
    - 해당 경기만 구독 중인 연결 → 웹소켓 연결 종료 (close code `4000`, reason `MATCH_ENDED`)
    - 다른 구독(다른 경기/채팅)도 있는 연결 → 연결은 유지하고 이 경기 구독만 서버 브로커에서 제거 (클라이언트에 별도 프레임 없음) `[확인 필요]`
    - MATCH_SUSPENDED_UNKNOWN은 재개될 수 있으므로 해제 대상이 아니다. `[확인 필요]`
    - 해제 예약은 메모리에만 있어 서버 재시작 시 유실된다. 재시작 후에는 신규 구독만 위 규칙으로 거절된다.

### 8-3. 선수/팀 조회
- 이번 범위 밖 `[미결]`.

---

## 9. 장애 대응 요약

| 상황 | 동작 |
|---|---|
| MLB API 타임아웃/5xx/429 | 6장 재시도 정책 (다음 실행 예약, 서버 로그 기록) |
| MLB API 4xx | 즉시 실패, ERROR 로그 |
| 같은 경기 중복 폴링 | PollingRegistry가 거부 |
| 같은 배치 중복 실행 | JobLock이 건너뜀 |
| 같은 타석 중복 저장 시도 | 저장된 index로 걸러냄 + UNIQUE로 방어, skip |
| FINAL인데 점수 없음 / 타석 저장 실패 | 확정 보류, 다음 폴링에서 재시도 |
| 허용되지 않은 상태 전이 | 거부 + WARN 로그, 폴링 유지 |
| 서버 재시작 | restore + 따라잡기 로직 |
| 상태 불명 한도 초과 | MATCH_SUSPENDED_UNKNOWN + 이벤트 |
| 종료된 경기 구독 | 10분 뒤 연결/구독 해제, 이후 신규 구독 거절 |

---

## 10. 테스트 케이스

| # | 내용 | 상태 |
|---|---|---|
| 1 | 정상 경기: SCHEDULED → LIVE → FINAL, 승리팀/점수/종료시각 저장, 이벤트 1회 발행 | 완료 (`TotalMatchServiceTest`) |
| 2 | 같은 경기 폴링 두 번 등록 시 하나만 동작 | 완료 (`InMemoryPollingRegistryTest`, `MatchSchedulerTest`) |
| 3 | 같은 타석 두 번 저장 시도 시 한 행만 존재 | 완료 (`SaveMatchServiceTest`) |
| 4 | DELAYED 후 LIVE 복귀 | 완료 |
| 5 | DELAYED 12시간 초과 → MATCH_SUSPENDED_UNKNOWN | 완료 |
| 6 | LIVE 중 5분 무갱신 + DELAYED 6시간 초과 | 완료 |
| 7 | 서버 다운 중 종료된 경기 backfill 후 FINAL 확정 | 완료 |
| 8 | 재시작 후 at_bat_index 갭 backfill | 완료 |
| 9 | API 429/5xx 재시도 후 한도 초과 시 실패 기록 | 완료 (`MlbApiRetryExecutorTest`) |
| 10 | 4xx 즉시 실패 | 완료 |
| 11 | 스탯 배치 두 번 실행해도 결과 동일, season_year 다르면 새 행 | 완료 (`SeasonStatServiceTest`) |
| 12 | 마스터에 없던 선수가 추가된 후 null 재매핑 | 일부 (쿼리 문법/매핑만 검증 `JpqlQuerySyntaxTest`, 실제 DB 실행 미검증) |
| 13 | 일정 API: 경기 없는 날 빈 배열, 잘못된 날짜 400, KST 경계 시각 경기 포함 여부 | 완료 (`MatchScheduleApiTest`) |
| 14 | 허용되지 않은 상태 전이(FINAL → LIVE) 거부 | 완료 (`MatchStatusTest`, `MatchStatusServiceTest`) |
| 15 | 종료된 경기 10분 뒤 구독 해제, 이후 신규 구독 거절 | 완료 (`EndedMatchDisconnectorTest`, `MatchSubscriptionInterceptorTest`) |

---

## 11. 구현 순서

1. ~~상태 전이표 + FINAL 확정 저장 + 이벤트 (베팅 선행 조건)~~ 완료
2. ~~`PollingRegistry`/`JobLock` 인터페이스 + 중복 폴링 방어~~ 완료
3. ~~재시도 정책, 지연/한도 처리~~ 완료 (재시도 한도 수치는 임시값)
4. ~~서버 다운 따라잡기, 인덱스/UNIQUE 선언~~ 완료
5. ~~null 재매핑, 팀 active 정정~~ 완료
6. 일정 조회 API ~~완료~~, 스냅샷 API `[미결]`
7. ~~스탯 저장 배치~~ 완료 (마감 시각·이적 처리 `[미결]`)
8. ~~STOMP 인증 (JWT 문서 연계)~~ 완료

### 배포 전 필요 작업
- 수동 마이그레이션 실행: `src/main/resources/db/manual/2026-10-09_match_system.sql`
    - match_status 컬럼 VARCHAR 변경 + 이전 값(BEFORE/PLAYED/FINISHED/SUSPENDED) 변환
    - UNIQUE 추가 전 중복 데이터 확인
    - 선수 스탯 테이블 재생성 (경기 단위 → 시즌 단위)
- 클라이언트 구독 주소 변경: `/topic/games/{id}/current-plays` → `/topic/matches/{matchId}`

## 12. 미결 사항

- [x] MLB API 엔드포인트 목록 → 4장 표 (시즌 스탯 엔드포인트는 실제 응답 확인 필요)
- [ ] 재시도 한도 수치 (현재 임시 5회)
- [x] 재시도 방식 → 다음 실행 예약 / 실패 상태 기록 → 서버 로그
- [ ] 일일 스탯 마감 시각 (현재 임시 KST 00:00)
- [x] 스탯 저장 단위 → 시즌 누적 (저장만, 조회 API 없음)
- [ ] 이적 선수의 시즌 스탯 행 분리 여부
- [ ] 스냅샷 응답 항목
- [x] 종료 경기 구독 처리 → 10분 뒤 연결/구독 해제
- [x] 없는 경기 구독 처리 → 거절
- [ ] `[확인 필요]` 태그 항목 검토 (5장 상태 매핑·추가 전이, 6장 확정 보류 등)
- [ ] 선수/팀 조회 API
- [ ] 베팅 시스템 책임 범위 (별도 문서)

---

## 13. 변경 이력

- 2026-10-09: 최초 작성
- 2026-10-09: 구현 반영
    - 상태 전이에 SCHEDULED → FINAL, DELAYED → FINAL 추가, MLB status 매핑 정의
    - 지연 시간 기준(delayed_since), 폴링 단계 DB 기록(polling_phase) 추가
    - 재시도: 다음 실행 예약 방식, 실패 기록은 서버 로그, 한도 임시 5회
    - 선수 스탯: 시즌 단위 저장으로 엔티티 변경
    - 종료 경기: 10분 뒤 구독 해제, 이후 신규 구독 거절
    - 3장 현황의 실제 코드와 다른 부분(선수 동기화 주기, 일정 범위, 이닝별 수비 저장) 정정
