-- 매치 시스템 설계(docs/design/MatchSystem) 반영용 수동 마이그레이션 (MySQL)
-- 자동 실행되지 않음. 서버를 새 코드로 띄우기 전에 한 번 실행할 것.

-- 1) match_status: Hibernate가 MySQL ENUM('BEFORE','PLAYED',...) 컬럼으로 만들었을 수 있어 VARCHAR로 변경
--    (ddl-auto=update는 기존 컬럼 타입을 바꾸지 않음. 확인: SHOW COLUMNS FROM Matches LIKE 'match_status';)
ALTER TABLE Matches MODIFY match_status VARCHAR(32) NOT NULL;

-- 2) 이전 상태값을 새 상태값으로 변환 (조회 조건이 새 이름으로만 비교되므로 필수)
UPDATE Matches SET match_status = CASE match_status
    WHEN 'BEFORE'    THEN 'SCHEDULED'
    WHEN 'PLAYED'    THEN 'LIVE'
    WHEN 'FINISHED'  THEN 'FINAL'
    WHEN 'SUSPENDED' THEN 'MATCH_SUSPENDED_UNKNOWN'
    ELSE match_status END;

-- 3) UNIQUE 추가 전 중복 확인. 결과가 있으면 정리 후 서버 구동 (중복이 있으면 Hibernate의 UNIQUE 생성이 조용히 실패함)
SELECT game_pk, COUNT(*) FROM Matches GROUP BY game_pk HAVING COUNT(*) > 1;
SELECT match_id, at_bat_index, COUNT(*) FROM match_play_log GROUP BY match_id, at_bat_index HAVING COUNT(*) > 1;

-- 4) 서버 구동 후 UNIQUE/인덱스가 생성되었는지 확인
-- SHOW INDEX FROM Matches;          -- uk_matches_game_pk
-- SHOW INDEX FROM match_play_log;   -- uk_match_play_log_match_at_bat, idx_match_play_log_batter_external_id, idx_match_play_log_pitcher_external_id
-- SHOW INDEX FROM play_event;       -- idx_play_event_match_play_log_id

-- 5) 선수 스탯 테이블: 경기 단위(player_id, match_id) -> 시즌 단위(player_external_id, season_year)로 구조 변경
--    기존 테이블에는 NOT NULL match_id 컬럼이 남아 신규 저장이 실패하므로 재생성 필요 (ddl-auto=update는 컬럼을 삭제하지 않음)
--    문서상 스탯은 아직 저장된 적이 없음. 아래 COUNT가 0인지 확인 후 DROP, 서버 구동 시 Hibernate가 새 구조로 생성
SELECT COUNT(*) FROM player_hitter_record;
SELECT COUNT(*) FROM player_pitcher_record;
-- DROP TABLE player_hitter_record;
-- DROP TABLE player_pitcher_record;
