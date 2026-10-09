package com.mlbbroadcast.member;

import com.mlbbroadcast.configuration.*;
import com.mlbbroadcast.member.dto.*;
import com.mlbbroadcast.util.RedisUtilities;
import com.mlbbroadcast.util.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;


import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {
    private final RestClient restClient = RestClient.create();
    private final TokenProvider tokenProvider;
    private final OauthProperties oauthProperties;
    private final AuthProperties authProperties;
    private final RedisUtilities redis;
    private final MembersRepository membersRepository;

    //구글 인가 로그인
    public TotalLoginResponse googleAuth(String code){

        OauthTokenResponse tokenResponse;

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", oauthProperties.getGoogleOauthId());
        form.add("client_secret", oauthProperties.getGoogleOauthSecret());
        form.add("redirect_uri", oauthProperties.getGoogleRedirectUri());
        form.add("grant_type", "authorization_code");

        try {
            tokenResponse = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OauthTokenResponse.class);
        }catch (RestClientResponseException e){
            log.info("oauth 설정 확인: idLen={}, redirectUri=[{}], secretNull={}",
                    oauthProperties.getGoogleOauthId() == null ? null : oauthProperties.getGoogleOauthId().length(), oauthProperties.getGoogleRedirectUri(), oauthProperties.getGoogleOauthSecret() == null);
            throw new BusinessException(ErrorCode.OAUTH_NOT_RESPONSE);
        }catch (ResourceAccessException e){
            throw new BusinessException(ErrorCode.NETWORK_ERROR);
        }

        //response NullPointer예외 처리
        if(tokenResponse==null || tokenResponse.token()==null) throw new BusinessException(ErrorCode.OAUTH_NOT_RESPONSE);

        String accessToken = tokenResponse.token();
        OauthUserInfoResponse userInfoResponse;

        try {
            userInfoResponse = restClient.get()
                    .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(OauthUserInfoResponse.class);
        }catch (RestClientResponseException e){
            throw new BusinessException(ErrorCode.OAUTH_NOT_RESPONSE);
        }catch (ResourceAccessException e){
            throw new BusinessException(ErrorCode.NETWORK_ERROR);
        }
        if(userInfoResponse == null || userInfoResponse.sub() == null) throw new BusinessException(ErrorCode.OAUTH_NOT_RESPONSE);


        return login(Provider.GOOGLE, userInfoResponse.sub());
    }

    private String registerTokenProvider(){
        return "signUp:"+UUID.randomUUID();
    }

    public TotalLoginResponse login(Provider provider, String sub){

        Optional<Members> member = membersRepository.findByProviderTypeAndProviderId(provider,sub);

        //존재하지 않는다면 -> 회원가입 요구 응답발송
        if(member.isEmpty()){

            String registerToken = registerTokenProvider();
            //중복 키 방지
            for(int count = 0; count < 3; count++) {
                //setIfAbsent는 파이프라인/트랜잭션에서 null을 반환할 수 있어 null도 실패로 처리
                if (Boolean.TRUE.equals(redis.setIfAbsent(registerToken, new RegisterRedisRecord(sub, provider), authProperties.getRegisterTempTokenDuration()))) {
                    break;
                }
                if(count == 2) throw new BusinessException(ErrorCode.AUTH_CONFLICT);
                registerToken = registerTokenProvider();
            }

            return new TotalLoginResponse(true, null, registerToken);


        }
        else {
            String jwtToken = tokenProvider.generateJwtToken(member.get().getId());
            return new TotalLoginResponse(false, new SuccessLoginResponse(jwtToken, member.get().getId()), null);
        }

    }

    public SuccessLoginResponse register(RegisterRequest request){

        RegisterRedisRecord registerCache = redis.load(request.registerToken(), RegisterRedisRecord.class);
        if(registerCache == null) throw new BusinessException(ErrorCode.REGISTER_TIME_OUT);


        if(membersRepository.existsByNickname(request.nickName())){
            throw new BusinessException(ErrorCode.REGISTER_DOUBLED_NAME);
        }

        Long memberId = saveMember(registerCache.sub(), registerCache.provider(), request.nickName());

        //가입은 이미 완료되었으므로 토큰 삭제 실패가 요청 실패로 이어지지 않도록 처리 (남은 토큰은 TTL로 만료)
        try {
            redis.remove(request.registerToken());
        } catch (Exception e) {
            log.warn("회원가입 임시 토큰 삭제 실패: memberId={}", memberId, e);
        }

        //토큰 발급이 실패해도 회원은 저장되어 있으므로 재로그인으로 복구 가능
        String jwtToken = tokenProvider.generateJwtToken(memberId);
        return new SuccessLoginResponse(jwtToken, memberId);
    }


    private Long saveMember(String sub, Provider provider, String nickName){
        Members newMember = Members.builder().providerId(sub).providerType(provider).nickname(nickName).createdAt(LocalDateTime.now()).build();
        try {
            return membersRepository.save(newMember).getId();
        } catch (DataIntegrityViolationException e) {
            if(membersRepository.existsByNickname(newMember.getNickname()))throw new BusinessException(ErrorCode.REGISTER_DOUBLED_NAME);
            else throw new BusinessException(ErrorCode.AUTH_CONFLICT);

        }
    }


}
