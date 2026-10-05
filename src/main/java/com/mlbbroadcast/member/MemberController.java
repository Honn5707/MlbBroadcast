package com.mlbbroadcast.member;

import com.mlbbroadcast.member.dto.OauthTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Controller("/api/auth")
@RequiredArgsConstructor
public class MemberController {


    private final MemberService memberService;

    @GetMapping("google/callback")
    public ResponseEntity<OauthTokenResponse> oauthLogin(String code){
        return  ResponseEntity.ok(memberService.googleAuth(code));
    }

}