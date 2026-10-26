package com.mlbbroadcast.match.service;

import com.mlbbroadcast.match.dto.MatchScheduleResponse;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchQueryService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MatchesRepository matchesRepository;

    //KST 하루 [00:00, 다음날 00:00)를 UTC 시각 범위로 바꿔 조회 (KST 하루에 미국 현지 날짜 두 개가 걸칠 수 있어 날짜 문자열이 아닌 시각 범위 사용)
    @Transactional(readOnly = true)
    public List<MatchScheduleResponse> findSchedule(LocalDate kstDate){
        LocalDateTime start = toUtc(kstDate);
        LocalDateTime end = toUtc(kstDate.plusDays(1));
        return matchesRepository
                .findAllByScheduledStartedTimeGreaterThanEqualAndScheduledStartedTimeLessThanOrderByScheduledStartedTimeAsc(start, end)
                .stream().map(MatchScheduleResponse::from).toList();
    }

    private LocalDateTime toUtc(LocalDate kstDate){
        return kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
