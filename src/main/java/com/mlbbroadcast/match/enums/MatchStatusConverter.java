package com.mlbbroadcast.match.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

//이전 상태값(BEFORE/PLAYED/FINISHED/SUSPENDED)이 남아 있어도 읽을 수 있도록 변환. 저장은 항상 새 이름으로 함
//기존 데이터는 db/manual 마이그레이션 SQL로 일괄 변환 필요 (조회 조건은 새 이름으로만 비교되므로)
@Converter
public class MatchStatusConverter implements AttributeConverter<MatchStatus, String> {

    @Override
    public String convertToDatabaseColumn(MatchStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public MatchStatus convertToEntityAttribute(String value) {
        if(value == null) return null;
        return switch (value) {
            case "BEFORE" -> MatchStatus.SCHEDULED;
            case "PLAYED" -> MatchStatus.LIVE;
            case "FINISHED" -> MatchStatus.FINAL;
            case "SUSPENDED" -> MatchStatus.MATCH_SUSPENDED_UNKNOWN;
            default -> MatchStatus.valueOf(value);
        };
    }
}
