package com.mlbbroadcast.match.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

//경기 상태와 허용 전이 (docs/design/MatchSystem 5장)
public enum MatchStatus {
    SCHEDULED, LIVE, DELAYED, FINAL, CANCELED, MATCH_SUSPENDED_UNKNOWN;

    private static final Map<MatchStatus, Set<MatchStatus>> ALLOWED_NEXT = new EnumMap<>(MatchStatus.class);

    static {
        //SCHEDULED -> FINAL, DELAYED -> FINAL: 서버 다운 중 종료된 경기 따라잡기, 지연 중 콜드게임 종료를 위해 추가
        ALLOWED_NEXT.put(SCHEDULED, EnumSet.of(LIVE, DELAYED, CANCELED, FINAL));
        ALLOWED_NEXT.put(DELAYED, EnumSet.of(LIVE, CANCELED, MATCH_SUSPENDED_UNKNOWN, FINAL));
        ALLOWED_NEXT.put(LIVE, EnumSet.of(FINAL, DELAYED, MATCH_SUSPENDED_UNKNOWN));
        ALLOWED_NEXT.put(MATCH_SUSPENDED_UNKNOWN, EnumSet.of(LIVE, FINAL, CANCELED));
        ALLOWED_NEXT.put(FINAL, EnumSet.noneOf(MatchStatus.class));
        ALLOWED_NEXT.put(CANCELED, EnumSet.noneOf(MatchStatus.class));
    }

    public boolean canTransitionTo(MatchStatus next){
        return ALLOWED_NEXT.get(this).contains(next);
    }

    public boolean isTerminal(){
        return ALLOWED_NEXT.get(this).isEmpty();
    }
}
