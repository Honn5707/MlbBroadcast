package com.mlbbroadcast.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

//구글 토큰 엔드포인트는 access_token 필드로 응답
public record OauthTokenResponse(@JsonProperty("access_token") String token) {
}
