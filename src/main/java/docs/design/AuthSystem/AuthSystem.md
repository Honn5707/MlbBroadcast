# JWT 인증/인가 (2026-10-08)

## 목적/범위
- 로그인한 사용자에게 JWT Access Token 발급 및 검증
- 범위 제외: 현재는 뼈대위주로 작성(실질적인 비로그인 제한 x). 추후 비로그인 허용 경로에 대한 범위를 좁힐예정,Refresh 토큰, 로그아웃, 블랙리스트

## 정책
- 토큰 TTL: jwtProperties.expireMs (값: 1h- application.yml)
- 클레임: sub(memberId) 만 사용. role/개인정보 없음
- 비로그인 허용 경로: /api/me 를 제외한 모든 경로 
- 그 외 경로: /api/me (필터 검증용 임시 경로, 보호 기능이 생기면 제거)
- 토큰 없음: 필터는 통과시키고 접근 제어는 SecurityConfig가 판단
- 토큰 무효(만료/위조/형식 오류): SecurityContext 비운 채 진행 → 보호 경로면 401 , 보호 경로가 아닐경우 통과

## 에러 응답
| 상황            | HTTP | ErrorCode                                                       |
|---------------|---|-----------------------------------------------------------------|
| 토큰 없음 (보호경로)  | 401 | AUTHENTICATION_REQUIRED |
| 토큰 만료 (보호경로)  | 401 | EXPIRED_TOKEN | 
| 토큰 위조 (보호 경로) | 401 | INVALID_TOKEN|
| 인증은 됐으나 권한 부족(미사용, 권한 기능 도입 시) | 403 | ACCESS_DENIED |                                                  |
- 응답은 BusinessException/기존 에러 바디 형식과 동일

## 결정 사항 (ADR)
-  /api/me를 제외한 모든 경로를 열어두는이유: 회원 시스템 구축 중이라 보호 대상 기능이 없고, 정책은 기능 확정 후 좁힌다
 - 무효 토큰을 필터에서 거절하지 않는 이유: 만료 토큰 때문에 공개 API까지 막히는 것을 방지하고, 접근 제어 판단을 SecurityConfig 한 곳에 모으기 위해.
- 웹소켓은 핸드셰이크 개방, 인증은 STOMP CONNECT에서 처리
- jwt sub 에는 memberId만 명시. 추후 인증/인가가 필요한 로직은 해당 sub사용
- /error는 Spring Boot가 기본으로 제공하는 에러 처리 경로