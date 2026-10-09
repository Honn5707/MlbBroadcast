package org.example.mlbbroadcast;

import com.mlbbroadcast.configuration.AuthProperties;
import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.configuration.OauthProperties;
import com.mlbbroadcast.member.MemberService;
import com.mlbbroadcast.member.Members;
import com.mlbbroadcast.member.MembersRepository;
import com.mlbbroadcast.member.Provider;
import com.mlbbroadcast.member.dto.RegisterRedisRecord;
import com.mlbbroadcast.member.dto.RegisterRequest;
import com.mlbbroadcast.member.dto.SuccessLoginResponse;
import com.mlbbroadcast.member.dto.TotalLoginResponse;
import com.mlbbroadcast.util.RedisUtilities;
import com.mlbbroadcast.util.TokenProvider;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
    @Mock
    TokenProvider tokenProvider;
    @Mock
    OauthProperties oauthProperties;
    @Mock
    AuthProperties authProperties;
    @Mock
    RedisUtilities redis;
    @Mock
    MembersRepository membersRepository;
    @InjectMocks
    MemberService memberService;

    private static final String SUB = "google-sub-123";
    private static final String REGISTER_TOKEN = "signUp:test-token";
    private static final String NICKNAME = "slugger";
    private static final Duration TEMP_DURATION = Duration.ofMinutes(10);

    private Members member(Long id) {
        Members member = Members.builder()
                .providerId(SUB)
                .providerType(Provider.GOOGLE)
                .nickname(NICKNAME)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("기존 회원이면 JWT를 발급하고 임시 토큰은 만들지 않는다")
        void loginSuccess() {
            when(membersRepository.findByProviderTypeAndProviderId(Provider.GOOGLE, SUB))
                    .thenReturn(Optional.of(member(1L)));
            when(tokenProvider.generateJwtToken(1L)).thenReturn("jwt");

            TotalLoginResponse response = memberService.login(Provider.GOOGLE, SUB);

            assertThat(response.isNewMember()).isFalse();
            assertThat(response.loginResponse()).isEqualTo(new SuccessLoginResponse("jwt", 1L));
            assertThat(response.registerTempToken()).isNull();
            verify(redis, never()).setIfAbsent(anyString(), any(), any());
        }

        @Test
        @DisplayName("신규 회원이면 Redis에 임시 토큰을 저장하고 회원가입 토큰을 반환한다")
        void newMemberReturnsRegisterToken() {
            when(membersRepository.findByProviderTypeAndProviderId(Provider.GOOGLE, SUB))
                    .thenReturn(Optional.empty());
            when(authProperties.getRegisterTempTokenDuration()).thenReturn(TEMP_DURATION);
            when(redis.setIfAbsent(anyString(), any(), eq(TEMP_DURATION))).thenReturn(true);

            TotalLoginResponse response = memberService.login(Provider.GOOGLE, SUB);

            assertThat(response.isNewMember()).isTrue();
            assertThat(response.loginResponse()).isNull();
            assertThat(response.registerTempToken()).startsWith("signUp:");
            verify(redis).setIfAbsent(eq(response.registerTempToken()),
                    eq(new RegisterRedisRecord(SUB, Provider.GOOGLE)), eq(TEMP_DURATION));
            verify(tokenProvider, never()).generateJwtToken(anyLong());
        }

        @Test
        @DisplayName("임시 토큰 키가 충돌하면 새 토큰으로 재시도한다")
        void retryWhenRegisterTokenCollides() {
            when(membersRepository.findByProviderTypeAndProviderId(Provider.GOOGLE, SUB))
                    .thenReturn(Optional.empty());
            when(authProperties.getRegisterTempTokenDuration()).thenReturn(TEMP_DURATION);
            when(redis.setIfAbsent(anyString(), any(), any())).thenReturn(false, true);

            TotalLoginResponse response = memberService.login(Provider.GOOGLE, SUB);

            assertThat(response.isNewMember()).isTrue();
            assertThat(response.registerTempToken()).startsWith("signUp:");
            verify(redis, times(2)).setIfAbsent(anyString(), any(), any());
        }

        @Test
        @DisplayName("임시 토큰 키가 3번 연속 충돌하면 AUTH_CONFLICT 예외가 발생한다")
        void throwWhenRegisterTokenCollidesThreeTimes() {
            when(membersRepository.findByProviderTypeAndProviderId(Provider.GOOGLE, SUB))
                    .thenReturn(Optional.empty());
            when(authProperties.getRegisterTempTokenDuration()).thenReturn(TEMP_DURATION);
            when(redis.setIfAbsent(anyString(), any(), any())).thenReturn(false);

            assertThatThrownBy(() -> memberService.login(Provider.GOOGLE, SUB))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_CONFLICT);
            verify(redis, times(3)).setIfAbsent(anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("register")
    class Register {

        private final RegisterRequest request = new RegisterRequest(REGISTER_TOKEN, NICKNAME);
        private final RegisterRedisRecord cache = new RegisterRedisRecord(SUB, Provider.GOOGLE);

        @Test
        @DisplayName("정상 가입 시 회원을 저장하고 JWT를 발급한 뒤 임시 토큰을 삭제한다")
        void registerSuccess() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class)).thenReturn(cache);
            when(membersRepository.existsByNickname(NICKNAME)).thenReturn(false);
            when(membersRepository.save(any(Members.class))).thenReturn(member(1L));
            when(tokenProvider.generateJwtToken(1L)).thenReturn("jwt");

            SuccessLoginResponse response = memberService.register(request);


            assertThat(response).isEqualTo(new SuccessLoginResponse("jwt", 1L));
            verify(redis).remove(REGISTER_TOKEN);
        }

        @Test
        @DisplayName("임시 토큰이 만료(없음)되면 REGISTER_TIME_OUT 예외가 발생한다")
        void throwWhenRegisterTokenExpired() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class)).thenReturn(null);

            assertThatThrownBy(() -> memberService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REGISTER_TIME_OUT);
            verify(membersRepository, never()).save(any());
            verify(tokenProvider, never()).generateJwtToken(anyLong());
        }

        @Test
        @DisplayName("닉네임이 이미 존재하면 REGISTER_DOUBLED_NAME 예외가 발생하고 임시 토큰은 유지된다")
        void throwWhenNicknameDuplicated() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class)).thenReturn(cache);
            when(membersRepository.existsByNickname(NICKNAME)).thenReturn(true);

            assertThatThrownBy(() -> memberService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REGISTER_DOUBLED_NAME);
            verify(membersRepository, never()).save(any());
            verify(redis, never()).remove(anyString());
        }

        @Test
        @DisplayName("저장 중 동시 요청으로 닉네임이 선점되면 REGISTER_DOUBLED_NAME 예외가 발생한다")
        void throwWhenNicknameTakenDuringSave() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class)).thenReturn(cache);
            // 사전 검사 시점엔 없었지만, 저장 실패 후 재조회 시엔 존재
            when(membersRepository.existsByNickname(NICKNAME)).thenReturn(false, true);
            when(membersRepository.save(any(Members.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate nickname"));

            assertThatThrownBy(() -> memberService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REGISTER_DOUBLED_NAME);
            verify(tokenProvider, never()).generateJwtToken(anyLong());
            verify(redis, never()).remove(anyString());
        }

        @Test
        @DisplayName("닉네임 외 제약조건(중복 provider_id 등) 위반 시 AUTH_CONFLICT 예외가 발생한다")
        void throwWhenProviderIdDuplicatedDuringSave() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class)).thenReturn(cache);
            when(membersRepository.existsByNickname(NICKNAME)).thenReturn(false, false);
            when(membersRepository.save(any(Members.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate provider_id"));

            assertThatThrownBy(() -> memberService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_CONFLICT);
            verify(tokenProvider, never()).generateJwtToken(anyLong());
            verify(redis, never()).remove(anyString());
        }
    }

    /**
     * 서비스가 명시적으로 처리하지 않는 장애 상황.
     * 현재 동작을 고정해 두는 테스트이며, 처리 방식을 바꾸면 기대값도 함께 바꿔야 한다.
     */
    @Nested
    @DisplayName("예외 발생 가능 상황")
    class UnexpectedFailures {

        private final RegisterRequest request = new RegisterRequest(REGISTER_TOKEN, NICKNAME);
        private final RegisterRedisRecord cache = new RegisterRedisRecord(SUB, Provider.GOOGLE);

        @Test
        @DisplayName("[login] setIfAbsent가 null을 반환하면 실패로 간주해 재시도하고, 3회 후 AUTH_CONFLICT 예외가 발생한다")
        void loginTreatsNullSetIfAbsentAsFailure() {
            // RedisTemplate.setIfAbsent는 파이프라인/트랜잭션 안에서 null을 반환할 수 있다
            when(membersRepository.findByProviderTypeAndProviderId(Provider.GOOGLE, SUB))
                    .thenReturn(Optional.empty());
            when(authProperties.getRegisterTempTokenDuration()).thenReturn(TEMP_DURATION);
            when(redis.setIfAbsent(anyString(), any(), any())).thenReturn(null);

            assertThatThrownBy(() -> memberService.login(Provider.GOOGLE, SUB))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_CONFLICT);
            verify(redis, times(3)).setIfAbsent(anyString(), any(), any());
        }

        @Test
        @DisplayName("[login] Redis 연결 실패 시 BusinessException으로 변환되지 않고 그대로 전파된다")
        void loginPropagatesRedisConnectionFailure() {
            when(membersRepository.findByProviderTypeAndProviderId(Provider.GOOGLE, SUB))
                    .thenReturn(Optional.empty());
            when(authProperties.getRegisterTempTokenDuration()).thenReturn(TEMP_DURATION);
            when(redis.setIfAbsent(anyString(), any(), any()))
                    .thenThrow(new RedisConnectionFailureException("redis down"));

            assertThatThrownBy(() -> memberService.login(Provider.GOOGLE, SUB))
                    .isInstanceOf(RedisConnectionFailureException.class);
            verify(redis, times(1)).setIfAbsent(anyString(), any(), any());
        }

        @Test
        @DisplayName("[login] DB 조회 실패 시 예외가 그대로 전파된다")
        void loginPropagatesDbFailure() {
            when(membersRepository.findByProviderTypeAndProviderId(Provider.GOOGLE, SUB))
                    .thenThrow(new DataAccessResourceFailureException("db down"));

            assertThatThrownBy(() -> memberService.login(Provider.GOOGLE, SUB))
                    .isInstanceOf(DataAccessResourceFailureException.class);
            verify(redis, never()).setIfAbsent(anyString(), any(), any());
        }

        @Test
        @DisplayName("[login] JWT 서명 키가 약하면 WeakKeyException이 그대로 전파된다")
        void loginPropagatesJwtFailure() {
            when(membersRepository.findByProviderTypeAndProviderId(Provider.GOOGLE, SUB))
                    .thenReturn(Optional.of(member(1L)));
            when(tokenProvider.generateJwtToken(1L)).thenThrow(new WeakKeyException("secret too short"));

            assertThatThrownBy(() -> memberService.login(Provider.GOOGLE, SUB))
                    .isInstanceOf(WeakKeyException.class);
        }

        @Test
        @DisplayName("[register] Redis 조회 실패 시 회원을 저장하지 않고 예외가 전파된다")
        void registerPropagatesRedisLoadFailure() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class))
                    .thenThrow(new RedisConnectionFailureException("redis down"));

            assertThatThrownBy(() -> memberService.register(request))
                    .isInstanceOf(RedisConnectionFailureException.class);
            verify(membersRepository, never()).save(any());
        }

        @Test
        @DisplayName("[register] DataIntegrityViolation 외의 DB 오류는 변환되지 않고 전파된다")
        void registerPropagatesNonIntegrityDbFailure() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class)).thenReturn(cache);
            when(membersRepository.existsByNickname(NICKNAME)).thenReturn(false);
            when(membersRepository.save(any(Members.class)))
                    .thenThrow(new DataAccessResourceFailureException("db down"));

            assertThatThrownBy(() -> memberService.register(request))
                    .isInstanceOf(DataAccessResourceFailureException.class);
            // catch 블록의 닉네임 재조회는 일어나지 않는다
            verify(membersRepository, times(1)).existsByNickname(NICKNAME);
            verify(redis, never()).remove(anyString());
        }

        @Test
        @DisplayName("[register] 저장 실패 후 닉네임 재조회도 실패하면 원본 대신 재조회 예외가 전파된다")
        void registerPropagatesFailureInsideCatch() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class)).thenReturn(cache);
            when(membersRepository.existsByNickname(NICKNAME))
                    .thenReturn(false)
                    .thenThrow(new DataAccessResourceFailureException("db down"));
            when(membersRepository.save(any(Members.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> memberService.register(request))
                    .isInstanceOf(DataAccessResourceFailureException.class);
        }

        @Test
        @DisplayName("[register] 저장 후 JWT 발급이 실패해도 임시 토큰은 삭제되어 있어 재로그인으로 복구할 수 있다")
        void registerRemovesTokenBeforeJwtIssue() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class)).thenReturn(cache);
            when(membersRepository.existsByNickname(NICKNAME)).thenReturn(false);
            when(membersRepository.save(any(Members.class))).thenReturn(member(1L));
            when(tokenProvider.generateJwtToken(1L)).thenThrow(new WeakKeyException("secret too short"));

            assertThatThrownBy(() -> memberService.register(request))
                    .isInstanceOf(WeakKeyException.class);
            verify(membersRepository, times(1)).save(any(Members.class));
            verify(redis).remove(REGISTER_TOKEN);
        }

        @Test
        @DisplayName("[register] 저장 성공 후 임시 토큰 삭제가 실패해도 가입은 정상 응답한다")
        void registerSucceedsWhenRedisRemoveFails() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class)).thenReturn(cache);
            when(membersRepository.existsByNickname(NICKNAME)).thenReturn(false);
            when(membersRepository.save(any(Members.class))).thenReturn(member(1L));
            when(tokenProvider.generateJwtToken(1L)).thenReturn("jwt");
            doThrow(new RedisConnectionFailureException("redis down")).when(redis).remove(REGISTER_TOKEN);

            SuccessLoginResponse response = memberService.register(request);

            assertThat(response).isEqualTo(new SuccessLoginResponse("jwt", 1L));
        }

        @Test
        @DisplayName("[register] 사용한 임시 토큰으로 다시 가입하면 REGISTER_TIME_OUT 예외가 발생한다")
        void registerTokenReplay() {
            when(redis.load(REGISTER_TOKEN, RegisterRedisRecord.class)).thenReturn(cache, (RegisterRedisRecord) null);
            when(membersRepository.existsByNickname(NICKNAME)).thenReturn(false);
            when(membersRepository.save(any(Members.class))).thenReturn(member(1L));
            when(tokenProvider.generateJwtToken(1L)).thenReturn("jwt");

            memberService.register(request);

            assertThatThrownBy(() -> memberService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REGISTER_TIME_OUT);
            verify(membersRepository, times(1)).save(any(Members.class));
        }
    }
}
