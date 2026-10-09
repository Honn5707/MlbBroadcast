package com.mlbbroadcast.member;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

//JWT 필터 검증용 임시 경로. 보호 대상 기능이 생기면 제거 (docs/design/AuthSystem.md 참고)
@RestController
public class MeController {

    @GetMapping("/api/me")
    public ResponseEntity<Long> me(@AuthenticationPrincipal Long memberId){
        return ResponseEntity.ok(memberId);
    }
}
