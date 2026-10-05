package com.mlbbroadcast.member;

import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.configuration.OauthProperties;
import com.mlbbroadcast.member.dto.OauthTokenResponse;
import com.mlbbroadcast.member.dto.OauthUserInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final RestClient restClient = RestClient.create();
    private final OauthProperties properties;

    //구글 인가 로그인
    public OauthTokenResponse googleAuth(String code){

        OauthTokenResponse tokenResponse = restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("code=" + code
                        + "&client_id=" + properties.getGoogleOauthId()
                        + "&client_secret=" + properties.getGoogleOauthSecret()
                        + "&redirect_uri=" + properties.getGoogleRedirectUri()
                        + "&grant_type=authorization_code")
                .retrieve()
                .body(OauthTokenResponse.class);

        //response NullPointer예외 처리
        if(tokenResponse==null) throw new BusinessException(ErrorCode.OAUTH_NOT_RESPONSE);

        String accessToken = tokenResponse.token();

        OauthUserInfoResponse userInfoResponse = restClient.get()
                .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(OauthUserInfoResponse.class);

        return





    }
}
