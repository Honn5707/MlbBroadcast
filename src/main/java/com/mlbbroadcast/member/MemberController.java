package com.mlbbroadcast.member;

import com.mlbbroadcast.member.dto.RegisterRequest;
import com.mlbbroadcast.member.dto.SuccessLoginResponse;
import com.mlbbroadcast.member.dto.TotalLoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MemberController {


    private final MemberService memberService;

    @GetMapping("/google/callback")
    public ResponseEntity<TotalLoginResponse> oauthLogin(@RequestParam String code){
        return  ResponseEntity.ok(memberService.googleAuth(code));
    }

    //회원 생성 요청이므로 POST + JSON body, RegisterRequest의 검증 어노테이션 적용
    @PostMapping("/register")
    public ResponseEntity<SuccessLoginResponse> register(@Valid @RequestBody RegisterRequest request){

        return ResponseEntity.ok(memberService.register(request));
    }

}
