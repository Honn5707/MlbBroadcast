-- 베팅 시스템 정합성 제약 (docs/design/Bet/BetSystemDesign.md, db/manual/2026-11-20_bet_system.sql 대응)

-- 한 회원은 한 경기에 한 번만 베팅 (중복 확인과 동시에 들어온 요청의 최종 방어선)
alter table bet add constraint uk_bet_member_match unique (member_id, match_id);
create index idx_bet_match_id on bet (match_id);
create index idx_bet_member_id on bet (member_id);

-- 같은 베팅의 같은 사유(차감/지급/환불)는 한 번만 기록 (이중 지급의 마지막 방어선). bet_id NULL은 제약 대상 아님
alter table coin_transaction add constraint uk_coin_transaction_bet_reason unique (bet_id, point_reason);
create index idx_coin_transaction_member_id on coin_transaction (member_id);

-- 경기별 배율. match_id UNIQUE: 한 번 계산된 배율은 다시 계산하지 않음
create table bet_settlement (
    id           bigint       not null auto_increment,
    match_id     bigint       not null,
    win_side     varchar(16)  not null,
    odds         decimal(6,4) not null,
    win_amount   bigint       not null,
    total_amount bigint       not null,
    created_at   datetime(6)  not null,
    primary key (id),
    constraint uk_bet_settlement_match unique (match_id)
) engine=InnoDB;
