-- 베팅 시스템 설계(docs/design/Bet/BetSystemDesign.md) 반영용 수동 마이그레이션 (MySQL)
-- 자동 실행되지 않음. 새 코드로 서버를 띄우기 전에 한 번 실행할 것.
-- (2026-11-20 확인: bet, coin_transaction 모두 0건)

-- 1) ENUM 컬럼 -> VARCHAR (Hibernate가 만든 MySQL ENUM에는 NONE/ERROR/FAILED가 없어 정산 시 저장 실패. ddl-auto=update는 타입을 바꾸지 않음)
ALTER TABLE bet MODIFY status VARCHAR(16) NOT NULL;
ALTER TABLE bet MODIFY bet_side VARCHAR(16) NOT NULL;
ALTER TABLE coin_transaction MODIFY point_reason VARCHAR(32) NOT NULL;

-- 2) UNIQUE 추가 전 중복 확인 (결과가 있으면 정리 후 서버 구동)
SELECT member_id, match_id, COUNT(*) FROM bet GROUP BY member_id, match_id HAVING COUNT(*) > 1;
SELECT bet_id, point_reason, COUNT(*) FROM coin_transaction WHERE bet_id IS NOT NULL GROUP BY bet_id, point_reason HAVING COUNT(*) > 1;
ㅁ
-- 3) 서버 구동 후 확인
-- SHOW INDEX FROM bet;               -- uk_bet_member_match, idx_bet_match_id, idx_bet_member_id
-- SHOW INDEX FROM coin_transaction;  -- uk_coin_transaction_bet_reason
-- SHOW INDEX FROM bet_settlement;    -- uk_bet_settlement_match
-- SHOW VARIABLES LIKE 'innodb_lock_wait_timeout';  -- 앱 커넥션에서는 3 (hikari connection-init-sql)
