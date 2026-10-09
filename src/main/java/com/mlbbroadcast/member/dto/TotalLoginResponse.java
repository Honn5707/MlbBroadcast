package com.mlbbroadcast.member.dto;

import jakarta.annotation.Nullable;

public record TotalLoginResponse(
        boolean isNewMember,

        @Nullable SuccessLoginResponse loginResponse,   // 기존 회원이면 여기 채움
        @Nullable String registerTempToken // 회원가입을 위한 임시 토큰(단순 해시 데이터)
) {

}
