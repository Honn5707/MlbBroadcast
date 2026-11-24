package com.mlbbroadcast.bet;

import com.mlbbroadcast.bet.dto.BetResponse;
import com.mlbbroadcast.bet.dto.PlaceBetRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bets")
@RequiredArgsConstructor
public class BetController {
    private final BetService betService;

    //인증 필요 (SecurityConfig.PROTECTED_PATHS). 베팅 회원은 토큰의 memberId
    @PostMapping
    public ResponseEntity<BetResponse> placeBet(@AuthenticationPrincipal Long memberId, @Valid @RequestBody PlaceBetRequest request){
        return ResponseEntity.ok(betService.placeBet(memberId, request));
    }
}
