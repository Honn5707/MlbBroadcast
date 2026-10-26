package com.mlbbroadcast.match;

import com.mlbbroadcast.match.dto.MatchScheduleResponse;
import com.mlbbroadcast.match.service.MatchQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {
    private final MatchQueryService matchQueryService;

    //{date}는 KST 기준 날짜(yyyy-MM-dd). 경기 없는 날은 빈 배열, 형식 오류는 400
    @GetMapping("/schedule/{date}")
    public ResponseEntity<List<MatchScheduleResponse>> schedule(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date){
        return ResponseEntity.ok(matchQueryService.findSchedule(date));
    }
}
