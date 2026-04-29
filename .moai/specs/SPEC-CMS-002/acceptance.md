# SPEC-CMS-002 Acceptance Criteria

> 본 문서는 spec.md의 모든 REQ-AUTH-*-D-* sub-requirement에 대응하는 Given/When/Then 형식의 인수 조건을 정의한다.
> 각 인수 조건은 자동화 테스트(JUnit 5 + Mockito + Testcontainers PostgreSQL, Vitest, Playwright)로 검증된다.
> 부모 SPEC: SPEC-CMS-001 §A. 회원·권한·로그인 (Bundle A) 인수기준의 상세화.

---

## A. 로그인 (REQ-AUTH-001-D-*)

### A-001 — 일반 로그인 성공

**Given** users 테이블에 status=ACTIVE, locked_until=NULL 인 사용자 'admin'이 BCrypt(strength=12)로 해싱된 비밀번호 'ValidP@ss123'와 함께 존재할 때
**When** POST /api/v1/auth/login 에 `{username:'admin', password:'ValidP@ss123', rememberMe:false}`를 전송하면
**Then** 200 OK 응답이 반환되고
**And** 응답 본문에 `accessToken` (JWT, exp=15분), `expiresIn=900`, `passwordExpired=false`가 포함되며
**And** `Set-Cookie` 헤더에 `refreshToken=...; HttpOnly; Secure; SameSite=Strict`가 설정되고 (Max-Age 미지정 — 세션 쿠키)
**And** refresh_tokens 테이블에 token_hash·expires_at(=now+7일) 행이 추가되며
**And** users.fail_count=0, users.last_login_at=now로 갱신되고
**And** login_history에 (user_id=admin, success=true, ip, user_agent) 행이 추가된다.

### A-002 — rememberMe 활성 시 영구 쿠키

**Given** A-001과 동일한 사용자
**When** `{username:'admin', password:'ValidP@ss123', rememberMe:true}`로 로그인하면
**Then** Set-Cookie 헤더에 `Max-Age=604800` (7일)이 포함된다.

### A-003 — 잘못된 비밀번호

**Given** users.username='admin' 사용자가 존재하고 fail_count=0
**When** POST /api/v1/auth/login에 `{username:'admin', password:'wrong'}`을 보내면
**Then** 401 Unauthorized + `{ "code": "AUTH_INVALID_CREDENTIALS" }`가 반환되고
**And** users.fail_count=1로 증가하며
**And** login_history에 (user_id=admin, success=false, failure_reason='INVALID_PASSWORD') 행이 추가된다.

### A-004 — 존재하지 않는 사용자 (enumeration 방지)

**Given** username='ghost'인 사용자가 존재하지 않을 때
**When** POST /api/v1/auth/login에 `{username:'ghost', password:'anything'}`을 보내면
**Then** 401 Unauthorized + `{ "code": "AUTH_INVALID_CREDENTIALS" }`가 반환되고 (A-003과 동일 응답)
**And** login_history에 (user_id=NULL, username_attempt='ghost', success=false, failure_reason='USER_NOT_FOUND') 행이 추가된다.

### A-005 — 잠긴 계정 로그인 시도

**Given** users.status='LOCKED', locked_until=now+10분인 사용자 'locked'
**When** 올바른 비밀번호로 로그인 시도하면
**Then** 423 Locked + `{ "code": "AUTH_ACCOUNT_LOCKED" }`가 반환되고
**And** login_history에 failure_reason='ACCOUNT_LOCKED' 행이 추가된다.

### A-006 — 비활성 계정 로그인 시도

**Given** users.status='INACTIVE'인 사용자
**When** 올바른 비밀번호로 로그인 시도하면
**Then** 401 + `AUTH_INVALID_CREDENTIALS` (단일 사유 응답)이 반환되고
**And** login_history에 failure_reason='ACCOUNT_INACTIVE' 행이 기록된다.

### A-007 — 비밀번호 만료 경고 (REQ-AUTH-001-D-4)

**Given** users.password_changed_at = now - 91일, status=ACTIVE인 사용자
**When** 올바른 자격증명으로 로그인하면
**Then** 200 OK + 응답 본문에 `passwordExpired=true`가 포함된다
**And** accessToken은 정상 발급되어 비밀번호 변경 화면으로 이동 가능하다.

### A-008 — IP 화이트리스트 제한 (REQ-AUTH-001-D-5)

**Given** SYSADMIN 계정에 IP 화이트리스트 `["10.0.0.0/24"]`가 등록되어 있고, 요청 IP가 192.168.1.10일 때
**When** 올바른 자격증명으로 로그인하면
**Then** 401 + `AUTH_INVALID_CREDENTIALS` (응답 일원화)가 반환되고
**And** login_history.failure_reason='IP_BLOCKED'으로 기록된다.

---

## B. 토큰 (REQ-AUTH-002-D-*, 003-D-*, 012-D-*)

### B-001 — Refresh Token 정상 갱신

**Given** 클라이언트가 유효한 Refresh Cookie(token_hash가 refresh_tokens에 존재, revoked_at IS NULL, expires_at > now)를 보유하고
**When** POST /api/v1/auth/refresh를 호출하면
**Then** 200 OK + 새 Access Token이 응답 본문에 포함되고
**And** 응답 Set-Cookie에 새 refreshToken이 설정되며
**And** 기존 refresh_tokens 행은 revoked_at=now, revoke_reason='ROTATION'으로 갱신되고
**And** 새 refresh_tokens 행이 INSERT된다.

### B-002 — 만료된 Refresh Token

**Given** refresh_tokens.expires_at < now인 토큰을 Cookie로 보유
**When** POST /api/v1/auth/refresh를 호출하면
**Then** 401 + `AUTH_REFRESH_INVALID`가 반환된다.

### B-003 — Refresh Token 탈취 감지

**Given** 사용자 'alice'의 Refresh Token이 정상 회전(B-001) 되어 revoked_at='2026-04-29 10:00:00, ROTATION'으로 표시된 후
**When** 공격자가 그 폐기된 토큰으로 POST /api/v1/auth/refresh를 호출하면
**Then** 401 + `AUTH_REFRESH_INVALID`가 반환되고
**And** 사용자 'alice'의 모든 활성 refresh_tokens가 revoked_at=now, revoke_reason='TOKEN_REUSE_DETECTED'로 즉시 폐기되며
**And** audit_log에 severity=CRITICAL 이벤트가 기록된다.

### B-004 — 미존재 Refresh Token

**Given** Cookie에 임의 랜덤 문자열이 들어 있을 때
**When** POST /api/v1/auth/refresh 호출하면
**Then** 401 + `AUTH_REFRESH_INVALID`가 반환된다.

### B-005 — 단일 디바이스 로그아웃

**Given** 인증된 사용자가 활성 Refresh Cookie 보유
**When** POST /api/v1/auth/logout 호출하면
**Then** 204 No Content + 만료된 Set-Cookie 헤더(Max-Age=0)가 반환되고
**And** 해당 refresh_tokens 행의 revoked_at=now, revoke_reason='LOGOUT'으로 갱신된다.

### B-006 — 전체 디바이스 로그아웃

**Given** 사용자 'bob'이 3개 디바이스에서 활성 Refresh Token 3개 보유
**When** 그중 한 디바이스에서 POST /api/v1/auth/logout-all 호출하면
**Then** 204 + 'bob'의 모든 활성 refresh_tokens가 revoked_at=now로 갱신된다.

### B-007 — 강제 로그아웃 (관리자) (REQ-AUTH-012-D-1)

**Given** SYSADMIN 'admin'이 인증되어 있고, 사용자 'target'이 활성 Refresh Token 2개 보유
**When** admin이 POST /api/v1/users/{target_id}/force-logout 호출하면
**Then** 204 + 'target'의 모든 refresh_tokens가 revoked_at=now, revoke_reason='FORCE_LOGOUT'으로 갱신되고
**And** audit_log에 (admin_id=admin, target_user_id=target, action='FORCE_LOGOUT') 기록된다.

### B-008 — 강제 로그아웃 후 Access Token 잔존 (REQ-AUTH-012-D-2)

**Given** B-007 직후, target의 Access Token(만료 5분 남음)이 클라이언트에 보관되어 있을 때
**When** target이 그 Access Token으로 보호된 API를 호출하면
**Then** 만료(15분 도달)까지는 200 응답 (1차 정책 — Access Token blacklist 미적용)
**And** 만료 이후 refresh 시도 시 모든 토큰이 revoked되어 401 반환된다.

### B-009 — 비밀번호 변경 시 Refresh 폐기 (REQ-AUTH-009-D-2)

**Given** 사용자가 비밀번호를 정상 변경한 직후
**When** refresh_tokens 테이블을 조회하면
**Then** 변경 전 모든 refresh_tokens 행이 revoked_at=now, revoke_reason='PASSWORD_CHANGED'으로 갱신되어 있다
**And** 변경 응답에 새 Access Token이 포함된다 (현재 디바이스 인증 유지).

---

## C. 비밀번호 (REQ-AUTH-004-D-*, 009-D-*, 010-D-*)

### C-001 — 비밀번호 정책 위반: 길이 부족

**Given** 새 사용자 등록 요청에 password='Ab!1'(4자) 가 포함될 때
**When** POST /api/v1/users 호출하면
**Then** 400 + `AUTH_PASSWORD_POLICY_VIOLATION`이 반환된다.

### C-002 — 비밀번호 정책 위반: 조합 부족

**Given** password='abcdefgh'(영문 소문자만, 1종)
**When** 등록 요청 시
**Then** 400 + `AUTH_PASSWORD_POLICY_VIOLATION`이 반환된다.

### C-003 — 비밀번호 정책 위반: username 포함

**Given** username='admin', password='Admin123!'
**When** 등록 요청 시
**Then** 400 + `AUTH_PASSWORD_POLICY_VIOLATION`이 반환된다.

### C-004 — 비밀번호 정책 통과

**Given** password='Abcdef!1'(8자, 영문 대·소·특수·숫자 4종)
**When** 등록 요청 시
**Then** 201 Created가 반환되고
**And** users.password_hash에 BCrypt strength=12 해시가 저장된다 (해시 prefix `$2a$12$` 또는 `$2b$12$` 검증).

### C-005 — 비밀번호 변경 — 현재 비밀번호 일치

**Given** 인증된 사용자가 currentPassword='OldP@ss12', newPassword='NewP@ss34'를 보유한 상태에서
**When** POST /api/v1/auth/password/change를 호출하면
**Then** 200 + 새 accessToken이 반환되고
**And** users.password_hash가 BCrypt(NewP@ss34)로 갱신되며
**And** users.password_changed_at=now 가 갱신되고
**And** password_history에 이전 password_hash가 INSERT된다.

### C-006 — 비밀번호 변경 — 현재 비밀번호 불일치

**Given** 인증된 사용자가 currentPassword='wrong'을 보냈을 때
**When** POST /api/v1/auth/password/change 호출
**Then** 400 + `AUTH_CURRENT_PASSWORD_MISMATCH`가 반환된다.

### C-007 — 비밀번호 재사용 금지 (직전 5개)

**Given** 사용자가 password_history에 직전 5개 비밀번호 해시를 보유
**When** newPassword가 직전 5개 중 하나와 일치하는 평문일 때 변경 요청하면
**Then** 400 + `AUTH_PASSWORD_REUSED`가 반환된다.

### C-008 — 비밀번호 재사용 검사 — 6번째 이전 비밀번호는 허용

**Given** 사용자의 password_history에 직전 6번째(가장 오래된) 비밀번호 해시가 존재
**When** newPassword가 그 6번째 평문과 동일하게 변경 요청 시
**Then** 200 OK로 변경이 성공한다 (직전 5개만 비교).

### C-009 — 비밀번호 재설정 요청 — 사용자 존재

**Given** users.email_hash=SHA256('alice@example.com')인 사용자
**When** POST /api/v1/auth/password/reset-request `{email:'alice@example.com'}` 호출
**Then** 200 + `{message:'메일 확인하세요'}` 반환
**And** password_reset_tokens에 (user_id=alice, token_hash, expires_at=now+30분) 행 추가
**And** SMTP로 alice 이메일 주소에 토큰 링크 발송된다.

### C-010 — 비밀번호 재설정 요청 — 사용자 미존재 (enumeration 방지)

**Given** email='ghost@example.com'에 해당하는 사용자가 없을 때
**When** /password/reset-request 호출
**Then** 200 + 동일한 응답 메시지 (C-009와 구별 불가)
**And** password_reset_tokens에 행이 추가되지 않고, 메일도 발송되지 않는다.

### C-011 — 비밀번호 재설정 확정 — 정상

**Given** password_reset_tokens.token_hash=SHA256('valid-token')이 존재하고 used_at IS NULL, expires_at>now
**When** POST /password/reset-confirm `{token:'valid-token', newPassword:'NewP@ss12'}` 호출
**Then** 200 + 비밀번호가 변경되고
**And** password_reset_tokens.used_at=now로 기록되며
**And** 해당 사용자의 모든 refresh_tokens가 revoke된다.

### C-012 — 비밀번호 재설정 확정 — 만료 토큰

**Given** password_reset_tokens.expires_at = now - 1분
**When** /password/reset-confirm 호출
**Then** 400 + `AUTH_RESET_TOKEN_EXPIRED`가 반환된다.

### C-013 — 비밀번호 재설정 확정 — 사용된 토큰 재사용

**Given** password_reset_tokens.used_at IS NOT NULL
**When** 동일 토큰으로 /password/reset-confirm 재호출
**Then** 400 + `AUTH_RESET_TOKEN_INVALID`가 반환된다.

---

## D. 잠금 (REQ-AUTH-005-D-*)

### D-001 — 실패 카운터 증가

**Given** users.fail_count=2 인 사용자
**When** 잘못된 비밀번호로 로그인 시도
**Then** users.fail_count=3 으로 증가한다.

### D-002 — 5회 도달 시 자동 잠금

**Given** users.fail_count=4 인 사용자
**When** 5회째 잘못된 비밀번호 시도하면
**Then** users.status='LOCKED', locked_until = now + 30분 으로 즉시 갱신되고
**And** 6회째 어떤 비밀번호 시도에도 423 + `AUTH_ACCOUNT_LOCKED`가 반환된다.

### D-003 — 잠금 자동 해제 (시간 경과)

**Given** users.status='LOCKED', locked_until = now - 1분 (이미 만료)
**When** 사용자가 올바른 비밀번호로 로그인 시도
**Then** 200 OK + 로그인 성공
**And** users.status='ACTIVE', fail_count=0, locked_until=NULL 로 복원된다.

### D-004 — 잠금 자동 해제 — 만료 후 잘못된 비밀번호

**Given** users.status='LOCKED', locked_until = now - 1분
**When** 잘못된 비밀번호로 시도하면
**Then** 401 + `AUTH_INVALID_CREDENTIALS`가 반환되고
**And** fail_count=1로 시작 (이전 카운터는 잠금 해제와 함께 리셋되어 새 카운트가 1).

### D-005 — 관리자 수동 잠금 해제

**Given** SYSADMIN이 인증된 상태에서 users.status='LOCKED'인 사용자 'target'
**When** POST /api/v1/users/{target_id}/unlock 호출
**Then** 200 OK + `{status:'ACTIVE'}` 반환
**And** users.status='ACTIVE', fail_count=0, locked_until=NULL 로 갱신
**And** audit_log에 (admin_id, target_id, action='UNLOCK') 기록된다.

### D-006 — IP별 시간당 30회 제한 (REQ-AUTH-005-D 보강)

**Given** 동일 IP에서 1시간 내 30회 로그인 시도(어떤 username이든 무관)
**When** 31회째 시도하면
**Then** 429 Too Many Requests + `RATE_LIMIT_EXCEEDED`가 반환된다.

---

## E. 사용자 CRUD (REQ-AUTH-006-D-*)

### E-001 — 관리자 사용자 생성 (임시 비밀번호 자동)

**Given** SYSADMIN 인증, 신규 username='newuser', email='new@x.com', forcePasswordChange=true
**When** POST /api/v1/users 호출
**Then** 201 + `{id, tempPassword:'<16자 랜덤>'}` 반환
**And** users 테이블에 (status=ACTIVE, password_hash=BCrypt(temp), force_password_change=TRUE 메타) 행 추가
**And** audit_log 적재.

### E-002 — 사용자 목록 페이징·필터

**Given** users 테이블에 100명이 있고 그중 30명이 status=ACTIVE, role='USER'
**When** GET /api/v1/users?page=0&size=20&status=ACTIVE&role=USER&q=hong 호출
**Then** 200 + `{content:[...], totalElements:30, totalPages:2}` 응답
**And** 응답에서 email/phone은 마스킹되어 있다 (REQ-CROSS-003 일반 운영자 권한).

### E-003 — 사용자 단건 조회

**Given** SYSADMIN 인증, users.id=42 존재
**When** GET /api/v1/users/42 호출
**Then** 200 + 사용자 상세 + roles=['USER'] 응답.

### E-004 — 사용자 미존재 조회

**When** GET /api/v1/users/99999 호출
**Then** 404 + `USER_NOT_FOUND` 반환.

### E-005 — 사용자 정보 수정 — username 변경 거부

**Given** SYSADMIN 인증
**When** PUT /api/v1/users/{id} 에 `{username:'newname', name:'홍길동'}` 보내면
**Then** name은 변경되지만 username은 무시된다 (immutable). audit_log에 변경 항목만 기록.

### E-006 — 사용자 비밀번호는 PUT으로 변경 불가

**When** PUT /api/v1/users/{id} 에 `{password:'NewP@ss12'}` 포함하여 호출
**Then** 400 + `AUTH_PASSWORD_CHANGE_NOT_ALLOWED_HERE` 반환 (별도 reset 절차 사용 안내).

### E-007 — 사용자 soft delete

**Given** users.id=42, 활성 refresh_tokens 2개
**When** SYSADMIN이 DELETE /api/v1/users/42 호출
**Then** 204 + users.deleted_at=now, status='DELETED'로 갱신
**And** 해당 사용자 모든 refresh_tokens가 revoke됨
**And** 이후 GET /api/v1/users 목록에 미포함.

### E-008 — 본인 정보 조회

**Given** 인증된 일반 사용자 'alice' (USER 역할)
**When** GET /api/v1/me 호출
**Then** 200 + `{id, username:'alice', email:평문, phone:평문, roles:['USER'], permissions:[...]}` 반환 (본인은 마스킹 X).

### E-009 — 본인 정보 수정 — 허용 항목

**Given** 인증된 alice
**When** PUT /api/v1/me `{name:'앨리스', email:'alice2@x.com', phone:'010-1111-2222'}` 호출
**Then** 200 + 변경된 본인 정보 반환
**And** users.email_enc/email_hash 갱신.

### E-010 — 본인 정보 수정 — status·role 변경 거부

**When** PUT /api/v1/me `{status:'INACTIVE', roleCodes:['SYSADMIN']}` 보내면
**Then** status·roleCodes는 무시되고 다른 항목만 처리되거나 400 응답이 반환된다 (구현 옵션). audit_log에 시도 기록.

---

## F. 역할/권한 (REQ-AUTH-007-D-*, 008-D-*)

### F-001 — 시스템 기본 역할 보장

**Given** Flyway V1 마이그레이션 실행 후
**When** SELECT * FROM roles WHERE is_system=TRUE 조회하면
**Then** SYSADMIN, CONTENT_ADMIN, USER 3개 행이 존재한다.

### F-002 — 시스템 역할 삭제 거부

**Given** SYSADMIN 인증
**When** DELETE /api/v1/roles/SYSADMIN (구현 시) 또는 PUT으로 code 변경 시
**Then** 400 + `ROLE_IS_SYSTEM_IMMUTABLE` 반환.

### F-003 — 신규 역할 생성

**When** SYSADMIN이 POST /api/v1/roles `{code:'EDITOR', name:'편집자', permissionCodes:['BOARD:WRITE','CONTENT:READ']}` 호출
**Then** 201 + roles에 'EDITOR' 추가
**And** role_permissions에 2개 매핑 INSERT.

### F-004 — 역할 코드 중복

**When** 이미 존재하는 code='USER'로 POST /api/v1/roles 호출
**Then** 409 + `ROLE_CODE_DUPLICATE` 반환.

### F-005 — 사용자에게 역할 매핑

**Given** SYSADMIN 인증, 사용자 'alice'에 USER 역할만 있을 때
**When** POST /api/v1/users/{alice_id}/roles `{roleCode:'EDITOR'}` 호출
**Then** 201 + user_roles에 (alice, EDITOR, granted_by=admin) 행 추가
**And** Caffeine 권한 캐시에서 alice 항목이 무효화되어 다음 요청 시 새 권한 반영.

### F-006 — 역할 회수

**Given** alice가 USER+EDITOR 보유
**When** DELETE /api/v1/users/{alice_id}/roles/EDITOR 호출
**Then** 204 + user_roles에서 매핑 삭제
**And** alice의 캐시 무효화 후 EDITOR 권한 즉시 박탈된다.

### F-007 — 메서드 레벨 권한 검사

**Given** alice 역할=USER (권한: ME:READ 만 보유)
**When** alice가 GET /api/v1/users (권한 USER:READ 필요) 호출
**Then** 403 + `AUTH_PERMISSION_DENIED` 반환.

### F-008 — 권한 캐시 hit/miss

**Given** alice가 처음 인증 후 5분 이내 두 번째 API 호출
**When** 두 번째 호출 시
**Then** Caffeine 캐시에서 권한 hit (DB 조회 없음, 메트릭으로 검증).
**And** 5분 + 1초 후 세 번째 호출 시 cache miss로 DB 재조회된다.

---

## G. 이력 (REQ-AUTH-011-D-*)

### G-001 — 모든 로그인 시도 기록

**Given** 어떤 로그인 시도(성공·실패·미존재 사용자)가 발생할 때
**When** API 처리 후
**Then** login_history 테이블에 시점·IP·UA·결과 행이 1건 추가되어 있다.

### G-002 — 관리자 사용자별 이력 조회

**Given** SYSADMIN 인증, 사용자 'alice'에 대한 로그인 이력 50건이 누적
**When** GET /api/v1/users/{alice_id}/login-history?page=0&size=20 호출
**Then** 200 + 시간 역순 페이징 결과 반환.

### G-003 — 본인 이력 조회

**Given** alice 인증
**When** GET /api/v1/me/login-history 호출
**Then** 200 + alice 본인 이력만 반환 (다른 사용자 이력 보이지 않음).

### G-004 — 이력 1년 보존 batch

**Given** login_history.created_at < now - 1년 인 행이 1000건 존재
**When** 일일 cleanup batch 실행
**Then** 해당 행이 삭제되고 audit_log에 정리 이벤트 기록.

---

## H. 품질 게이트 (Bundle A 한정)

### QG-A-1 — 보안 (Secured)

**Given** Bundle A 모듈이 빌드된 상태
**When** 보안 검사를 수행하면
**Then** 다음을 모두 만족한다:
- JWT 비밀키가 환경변수에서 주입되며 application.yml에 평문 노출 없음
- BCryptPasswordEncoder의 strength 파라미터가 12로 설정됨
- HTTPS가 강제됨 (Spring Security `requiresChannel().anyRequest().requiresSecure()` 또는 nginx HSTS)
- Refresh Cookie에 HttpOnly·Secure·SameSite=Strict 모두 설정됨
- 모든 비밀번호 평문이 메모리에서 사용 즉시 폐기됨 (가비지 컬렉션 친화적 char[] 사용 또는 String 즉시 null화)
- OWASP Dependency Check 결과 Critical/High 0건.

### QG-A-2 — 성능 (Performance)

**Given** 통합 테스트 환경(Testcontainers PostgreSQL)에서
**When** 로그인 API에 대해 100회 부하 테스트(JMeter 또는 k6)를 수행하면
**Then** p95 응답 시간 < 200ms (BCrypt 250ms 제외 시 50ms 목표; BCrypt 포함 p95는 < 300ms)이고
**And** 토큰 갱신 API p95 < 50ms이다.

### QG-A-3 — 가용성 (Availability)

**Given** Bundle A 통합 테스트 스위트
**When** 잠금 정책 시나리오(D-001~D-005) 자동화 테스트를 실행하면
**Then** 5회 실패 정확히 후 잠금, locked_until 정확히 30분, 자동 해제, 수동 해제 4가지가 모두 통과한다.

### QG-A-4 — 감사 (Auditability)

**Given** Bundle A 모든 인증 이벤트(로그인 성공·실패·로그아웃·비밀번호 변경·역할 변경·강제 로그아웃)
**When** 통합 테스트 후 audit_log 테이블 조회
**Then** §9.5의 모든 이벤트 유형이 1회 이상 기록되어 있다
**And** audit_log에 대한 UPDATE/DELETE 시도가 PostgreSQL 권한 오류로 실패한다 (REQ-CROSS-005).

### QG-A-5 — 접근성 (KWCAG 2.2 AA)

**Given** Admin SPA의 로그인 화면, 비밀번호 변경 화면, 비밀번호 재설정 요청·확정 화면
**When** Playwright + @axe-core/playwright로 자동 검사를 수행하면
**Then** critical/serious 위반 0건이고
**And** 키보드만으로 (Tab/Shift+Tab/Enter) 로그인 → 비밀번호 변경 워크플로우 완료 가능
**And** 로그인 실패 시 에러 메시지가 ARIA live region(`aria-live="polite"`)에 안내되며 스크린리더 음성 출력
**And** 모든 input에 `<label>` 또는 `aria-label` 부여
**And** 색대비 4.5:1 이상 (axe 자동 검증).

### QG-A-6 — RFP 비기능 임계값 (v0.2 추가, SPEC-CMS-001 v0.2 §17 매핑)

**Given** v0.2 amendment 빌드 산출물에 대해 부하·결함 운영 시뮬레이션이 가능한 환경에서
**When** 인증 API(`/auth/login`, `/auth/refresh`) k6 부하 테스트(동시 사용자 1,000명, 초당 50건, 10분)를 수행하면
**Then** 다음을 모두 만족한다:
- 정상 부하 p95 < 200ms 달성 (BCrypt 비용 제외 측정값 보고)
- BCrypt 포함 p95 < 3초 (PER-003 상한)
- CPU/Memory/Disk 평균 사용률 < 90% 유지 (PER-002)
- 시험 운영 기간 결함 발생률 < 5% (QUR-004 → QG-COMMON-1)
- P0 결함 탐지~복구 < 1시간 (QUR-004 → QG-COMMON-2)
- 고유식별번호(이메일·휴대폰) 컬럼이 AES-256-GCM 암호화 저장됨 (DB dump grep으로 평문 미존재 검증, SER-002).

---

## H. 4단계 RBAC (REQ-AUTH-013-D-*, v0.2)

### H-001 — 4단계 표준 역할 시드 보장

**Given** Flyway V2 마이그레이션 실행 후
**When** `SELECT code FROM roles WHERE is_system=TRUE ORDER BY code` 조회하면
**Then** SUPER_ADMIN, DEPT_ADMIN, EDITOR, VIEWER 4개 행이 모두 존재한다 (v0.1 SYSADMIN/CONTENT_ADMIN/USER 시드와 공존).

### H-002 — 역할 템플릿 권한 매핑 검증

**Given** V2 마이그레이션 직후
**When** `SELECT role_code, count(*) FROM role_permissions WHERE role_code IN ('SUPER_ADMIN','DEPT_ADMIN','EDITOR','VIEWER') GROUP BY 1` 실행
**Then** 각 역할의 시드 권한 개수가 §13.1 REQ-AUTH-013-D-2 정의(SUPER_ADMIN 전체, DEPT_ADMIN 도메인 한정, EDITOR 작성권, VIEWER 읽기 전용)와 일치한다.

### H-003 — 역방향 위임 거부

**Given** DEPT_ADMIN으로 인증된 사용자 'manager'
**When** manager가 `POST /api/v1/users/{target_id}/roles {roleCode:'SUPER_ADMIN'}` 호출
**Then** 403 + `AUTH_ROLE_ESCALATION_DENIED`가 반환되고
**And** permission_change_history에 (change_type='DENIED_ATTEMPT', target_resource='SUPER_ADMIN', changed_by=manager) 행이 기록된다.

### H-004 — 정방향 위임 허용

**Given** SUPER_ADMIN 'root'으로 인증
**When** root가 `POST /api/v1/users/{target_id}/roles {roleCode:'DEPT_ADMIN'}` 호출
**Then** 201 + user_roles 매핑 추가
**And** permission_change_history에 (change_type='ROLE_ASSIGN', target_resource='DEPT_ADMIN', changed_by=root) 기록.

### H-005 — 메뉴 × 역할 × 액션 매트릭스 조회

**Given** SUPER_ADMIN 인증
**When** `GET /api/v1/admin/permission-matrix` 호출
**Then** 200 + `{rows:[{resource, action, roles:{SUPER_ADMIN:bool, DEPT_ADMIN:bool, EDITOR:bool, VIEWER:bool}}]}` 형식의 매트릭스 응답이 반환된다.

### H-006 — 매트릭스 셀 토글 시 이력 자동 적재

**Given** SUPER_ADMIN 인증, 현재 EDITOR 역할에 `BOARD:DELETE` 권한 미부여
**When** SUPER_ADMIN이 매트릭스 화면에서 해당 셀을 토글(`POST /api/v1/admin/permission-matrix {roleCode:'EDITOR', permissionCode:'BOARD:DELETE', enabled:true}`)
**Then** 200 + role_permissions에 매핑 추가
**And** permission_change_history에 (change_type='PERM_ATTACH', target_resource='BOARD:DELETE', after_value='EDITOR') 자동 기록.

---

## I. 부서·조직 관리 (REQ-AUTH-014-D-*, v0.2)

### I-001 — organization 트리 깊이 제한

**Given** organization에 4단계 깊이 행 (`/1/2/3/4/`)이 존재
**When** 5단계 자식 추가 (`POST /api/v1/organizations {parent_id: 4단계 id}`)
**Then** 201 + 5단계 행 INSERT (depth=5)
**And** 6단계 추가 시도 시 400 + `ORG_DEPTH_EXCEEDED` 반환.

### I-002 — users.organization_id NULL 허용 (v0.1 사용자 호환)

**Given** v0.1 마이그레이션 직후의 기존 사용자(organization_id=NULL)
**When** v0.2 마이그레이션 적용 후 동일 사용자로 로그인
**Then** 200 OK + 정상 인증 (organization_id=NULL 인 users는 v0.2에서도 그대로 동작).

### I-003 — DEPT_ADMIN 자기 부서 사용자 조회

**Given** DEPT_ADMIN 'mgr'(organization.path='/1/3/')으로 인증
**When** `GET /api/v1/users?page=0&size=20` 호출
**Then** 200 + 응답 content는 `users.organization.path LIKE '/1/3/%'` 인 사용자 + organization_id=NULL 인 사용자만 포함 (다른 부서 사용자 제외).

### I-004 — DEPT_ADMIN 타 부서 사용자 조작 거부

**Given** DEPT_ADMIN 'mgr'(path='/1/3/')으로 인증, 대상 사용자 'other'(path='/1/5/')
**When** mgr가 `PUT /api/v1/users/{other_id} {name:'changed'}` 호출
**Then** 403 + `AUTH_ORG_SCOPE_DENIED` 반환
**And** audit_log에 차단 이벤트 기록.

### I-005 — 조직 변경 이력 자동 적재

**Given** SUPER_ADMIN이 organization id=12 행의 name을 'A팀' → 'A부'로 UPDATE
**When** UPDATE 트랜잭션 커밋 직후
**Then** organization_history 테이블에 (org_id=12, change_type='UPDATE', snapshot=수정 후 jsonb, changed_by=SUPER_ADMIN id) 행이 1건 추가된다.

---

## J. SSO 옵션 인터페이스 (REQ-AUTH-015-D-*, v0.2)

### J-001 — NoOpSsoProvider 기본 등록

**Given** Spring Boot 1차 빌드 (`auth.sso.enabled` 미설정)
**When** ApplicationContext에서 `SsoProvider` 빈을 조회
**Then** `NoOpSsoProvider` 인스턴스 1개만 등록되어 있다 (SamlSsoProvider, OidcSsoProvider 빈 미등록).

### J-002 — NoOpSsoProvider 동작 — 일반 로그인 무영향

**Given** J-001 환경에서 일반 사용자 'alice'가 ID/비밀번호로 `POST /api/v1/auth/login` 호출
**When** 응답 수신
**Then** 200 OK + 정상 accessToken 발급 (SSO 경로 미진입, 기존 v0.1 REQ-AUTH-001-D-1 그대로 동작).

### J-003 — NoOpSsoProvider 메서드 호출 시 안전 실패

**Given** 테스트 코드에서 `ssoProvider.authenticate("dummy")` 직접 호출
**When** 메서드 실행
**Then** `UnsupportedOperationException("SSO not configured")` 또는 빈 `SsoAuthResult.empty()`가 반환되어 호출자가 안전하게 처리할 수 있다 (런타임 충돌 없음).

---

## K. 권한 변경 이력 + 비인가 사전 차단 (REQ-AUTH-016-D-*, v0.2)

### K-001 — 정상 권한 부여 시 이력 자동 적재

**Given** SUPER_ADMIN 'root' 인증, 대상 'alice'에 EDITOR 미부여
**When** root가 `POST /api/v1/users/{alice_id}/roles {roleCode:'EDITOR'}` 호출
**Then** 201 + user_roles 매핑 추가
**And** permission_change_history에 (target_user_id=alice, change_type='ROLE_ASSIGN', target_resource='EDITOR', changed_by=root, ip_address) 1행 기록 (동일 트랜잭션).

### K-002 — 권한 회수 시 이력 적재

**Given** alice가 EDITOR 보유
**When** SUPER_ADMIN이 `DELETE /api/v1/users/{alice_id}/roles/EDITOR` 호출
**Then** 204 + 매핑 삭제
**And** permission_change_history에 change_type='ROLE_UNASSIGN' 행 추가.

### K-003 — 비인가 사용자 사전 차단

**Given** EDITOR 역할의 'alice'가 인증 (USER:WRITE 미보유)
**When** alice가 `POST /api/v1/users/{victim_id}/roles {roleCode:'EDITOR'}` 호출
**Then** 403 + `AUTH_PERMISSION_DENIED` 반환 (차단)
**And** permission_change_history에 (change_type='DENIED_ATTEMPT', target_user_id=victim, target_resource='EDITOR', changed_by=alice, ip_address) 행 기록
**And** audit_log에 severity=CRITICAL 동시 기록.

### K-004 — 권한 변경 이력 검색 API

**Given** SUPER_ADMIN 인증, permission_change_history에 100건 이력
**When** `GET /api/v1/admin/permission-history?targetUserId=42&changeType=ROLE_ASSIGN&from=2026-04-01&to=2026-04-30&page=0&size=20` 호출
**Then** 200 + 시간 역순 페이징 결과 + 필터 일치 행만 반환.

### K-005 — SUPER_ADMIN 부여 시 CRITICAL 알림

**Given** 활성 SUPER_ADMIN 3명('root1','root2','root3')이 존재, 대상 'alice'에 SUPER_ADMIN 미부여
**When** 'root1'이 alice에 SUPER_ADMIN을 부여하면
**Then** 201 + user_roles 매핑 추가
**And** root2, root3의 인앱 알림 큐에 "SUPER_ADMIN granted to alice by root1" 메시지 적재
**And** SMTP 활성 시 root2/root3 이메일에 동일 알림 발송
**And** audit_log에 (severity=CRITICAL, action='SUPER_ADMIN_GRANT') 기록.

---

## L. Definition of Done (Bundle A 완료 기준, v0.2)

본 SPEC-CMS-002 v0.2는 다음을 모두 만족할 때 완료된 것으로 간주한다.

- v0.1 기준: 모든 REQ-AUTH-001-D ~ REQ-AUTH-012-D sub-requirement가 구현되고 acceptance.md A~G의 모든 시나리오가 자동화 테스트로 통과
- v0.2 추가: REQ-AUTH-013-D ~ REQ-AUTH-016-D sub-requirement가 구현되고 H~K의 모든 시나리오가 통과
- QG-A-1~6의 6개 품질 게이트가 CI에서 모두 PASS (QG-A-6는 v0.2 신규)
- Flyway V1 + V2 마이그레이션이 PostgreSQL 16에서 0 오류 순차 적용
- JaCoCo 커버리지: domain.auth + domain.organization 패키지 line/branch 모두 ≥ 85%
- Vitest 커버리지: Admin SPA의 인증·권한 매트릭스·조직 화면 컴포넌트 line ≥ 85%
- OpenAPI 3.1 스펙 자동 생성 후 Swagger UI에서 §6 + §13 신규 엔드포인트(`/admin/permission-matrix`, `/admin/permission-history`, `/organizations/*`) 모두 노출됨
- 보안 담당자 검수: JWT/BCrypt/Refresh + 4단계 RBAC + 권한 변경 이력 정책 검증 완료 서명
- 운영 매뉴얼: 사용자·역할·잠금 해제·조직 트리·권한 매트릭스 절차가 한국어로 문서화됨

---

_문서 버전: v0.2 (2026-04-29 RFP 통합 amendment)_
_작성일: 2026-04-29_
_총 시나리오: A 8 + B 9 + C 13 + D 6 + E 10 + F 8 + G 4 + H 6 + I 5 + J 3 + K 5 + Quality Gate 6 = 83개 (v0.1 63개 + v0.2 추가 20개)_
