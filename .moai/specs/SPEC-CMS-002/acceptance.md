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

### H-007 — 역할 alias 해석 (REQ-AUTH-013-D-5, v0.3.2 사용자 결정 2026-04-29 Q-4 적용)

**Given** Flyway V2.1 마이그레이션 후 `roles` 테이블에 SYSADMIN row의 `aliased_to='SUPER_ADMIN'`이 설정되어 있고, SUPER_ADMIN 전용 메뉴 `/admin/system-config`가 `role_permissions(role_code='SUPER_ADMIN', permission_code='SYSTEM:WRITE')` 시드를 보유하며, 사용자 'legacy_admin'이 user_roles에 SYSADMIN 역할만 매핑된 상태에서
**When** legacy_admin이 인증 후 `GET /admin/system-config`(SUPER_ADMIN 전용)에 접근
**Then** 200 OK가 반환되고(권한 검사 통과 — alias 해석 결과 SUPER_ADMIN 권한 집합으로 평가)
**And** audit_log에 (event='role_alias_resolved', from_code='SYSADMIN', to_code='SUPER_ADMIN', user_id=legacy_admin.id) 행이 1건 적재된다.

### H-008 — alias 체인 금지 (REQ-AUTH-013-D-5, v0.3.2)

**Given** SYSADMIN.aliased_to='SUPER_ADMIN' 상태에서
**When** 운영자가 `UPDATE roles SET aliased_to='SOMETHING_ELSE' WHERE code='SUPER_ADMIN'`을 시도(SUPER_ADMIN을 다시 alias로 만드는 시도)
**Then** `chk_roles_alias_no_chain` 제약(또는 BEFORE UPDATE 트리거)에 의해 거부되며, SQLSTATE 23514 또는 동급 오류가 반환된다.

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

## L. 본인인증 (REQ-AUTH-017-D-*, v0.3.1 — 이메일 OTP 전용)

> **NOTE — v0.4+ 후속 검토 (사용자 결정 2026-04-29 Q-1 적용)**
> v0.3.1 1차는 **이메일 OTP만 유지**한다. SMS 채널 시나리오(L-001 원본, L-003 재발송 쿨다운의 SMS 케이스, L-009 SmsProvider Bean 검증)는 v0.4+ 후속 검토로 미룬다. 본 §L의 L-001은 v0.3.1에서 SMS 채널 차단 검증으로 대체되었으며, L-002 이후 시나리오는 EMAIL 채널 기준으로 동작한다.

### L-001 — SMS 채널 차단 검증 (v0.3.1, REQ-AUTH-017-D-1)

**Given** 인증된 사용자 'alice'가 v0.3.1 1차 환경(EMAIL 채널만 허용)에서
**When** alice가 `POST /api/v1/auth/verify/request {channel:'SMS', target:'01012345678', purpose:'IMPORTANT_CHANGE'}` 호출
**Then** 400 Bad Request + `{ "code":"VERIFY_CHANNEL_NOT_SUPPORTED" }`가 반환되고
**And** verification_request에 행이 INSERT되지 않으며
**And** 어떤 외부 발송 경로(SMS/EMAIL)도 호출되지 않는다.
**And** 응답 메시지에는 "SMS 채널은 v0.4+ 후속 검토 — 현재 EMAIL만 지원"이라는 안내가 포함될 수 있다 (구현 옵션).

> **L-001-Original (v0.4+ 후속 검토)**
> v0.3 원본 SMS 정상 발송 시나리오(SmsProvider.sendOtp 호출 검증)는 v0.4+ SMS 채널 활성화 시 재도입 예정.

### L-002 — 인증 요청 — EMAIL 채널 정상

**Given** 익명 사용자가 회원가입 도중 이메일 'new@x.com' 입력
**When** `POST /api/v1/auth/verify/request {channel:'EMAIL', target:'new@x.com', purpose:'SIGNUP'}` 호출
**Then** 200 + `{request_id, expires_in:300}` 반환
**And** verification_request 행 INSERT (channel='EMAIL', requester_id=NULL, requester_ip=요청 IP)
**And** Spring Mail로 'new@x.com' 주소에 6자리 OTP가 포함된 메일 발송된다.

### L-003 — 재발송 쿨다운 (1분 이내, EMAIL 기준)

**Given** L-002가 방금 처리되어 verification_request에 'new@x.com' 행이 1건 존재 (created_at=now-30s)
**When** 동일 target='new@x.com'으로 즉시 재요청
**Then** 429 + `VERIFY_RESEND_COOLDOWN`이 반환되고
**And** verification_request에 신규 행이 INSERT되지 않으며
**And** Spring Mail 발송도 호출되지 않는다.

> **L-003-SMS (v0.4+ 후속 검토)**: SMS target('01012345678') 기준 쿨다운 검증은 v0.4+ SMS 채널 활성화 시 재도입.

### L-004 — OTP 검증 정상

**Given** verification_request 행이 (request_id='r-1', code_hash=BCrypt('123456'), status='PENDING', expires_at=now+4m, attempts=0)으로 존재
**When** `POST /api/v1/auth/verify/confirm {request_id:'r-1', code:'123456'}` 호출
**Then** 200 + `{ "verified":true }` 반환
**And** verification_request.status='VERIFIED', verified_at=now로 갱신
**And** verification_history에 (target, success=true, ip_address, user_agent, occurred_at) 1행 적재.

### L-005 — OTP 검증 실패 — 코드 불일치 1회

**Given** verification_request (code_hash=BCrypt('123456'), attempts=0, max_attempts=3, status='PENDING')
**When** `POST /api/v1/auth/verify/confirm {request_id, code:'999999'}` 호출
**Then** 401 + `VERIFY_CODE_INVALID` 반환
**And** verification_request.attempts=1로 증가, status='PENDING' 유지
**And** verification_history에 success=false 행 추가.

### L-006 — OTP 검증 실패 — 3회 시도 후 차단

**Given** verification_request (attempts=2, max_attempts=3, status='PENDING')
**When** 잘못된 코드로 3회째 검증 호출
**Then** 423 + `VERIFY_BLOCKED` 반환
**And** verification_request.status='FAILED', attempts=3으로 갱신
**And** 동일 request_id로 재시도 시 423 + `VERIFY_BLOCKED` 반환 (status='FAILED' 영구 차단).

### L-007 — OTP 만료

**Given** verification_request.expires_at = now - 1분, status='PENDING'
**When** `/auth/verify/confirm` 호출
**Then** 401 + `VERIFY_CODE_EXPIRED` 반환 (status='EXPIRED'로 갱신).

### L-008 — IP 부정 시도 차단 (REQ-AUTH-017-D-5)

**Given** 동일 IP '203.0.113.10'에서 1시간 내 verification_history에 10건 시도 누적
**When** 11회째 `/auth/verify/request` 또는 `/auth/verify/confirm` 호출
**Then** 429 + `VERIFY_RATE_LIMIT_EXCEEDED` 반환
**And** audit_log에 (severity=CRITICAL, action='VERIFY_RATE_LIMIT', ip_address='203.0.113.10') 기록
**And** 1시간 동안 동일 IP의 verify 계열 호출이 차단된다.

### L-009 — SmsProvider placeholder 검증 (v0.3.1)

**Given** v0.3.1 1차 빌드 (`auth.sms.provider` 무시됨, EMAIL 채널만 허용)
**When** ApplicationContext에서 `SmsProvider` 빈 조회
**Then** `NoOpSmsProvider` 인스턴스 1개만 등록되어 있고 (NhnCloud/NaverCloud/AwsSns/Aligo 어댑터 skeleton은 패키지 트리에 포함되지 않음)
**And** 일반 본인인증 흐름(L-002 EMAIL 정상)에서는 `SmsProvider`가 전혀 호출되지 않는다 (EMAIL 채널은 Spring Mail만 사용)
**And** L-001 SMS 차단 검증에서도 channel 검증 단계에서 즉시 거부되어 `SmsProvider`까지 도달하지 않는다.

> **L-009-Adapters (v0.4+ 후속 검토 — 사용자 결정 2026-04-29 Q-1)**
> NhnCloud/NaverCloud/AwsSns/Aligo 어댑터 skeleton + ConditionalOnProperty 분기 + 실제 sendOtp 호출 검증은 v0.4+ SMS 채널 활성화 시 별도 SPEC(예: SPEC-CMS-SMS-001)로 위임.

---

## M. 회원정보 접근 로그 (REQ-AUTH-018-D-*, v0.3)

### M-001 — AOP 자동 적재 — 관리자가 다른 사용자 조회

**Given** SUPER_ADMIN 'root'(viewer_id=1) 인증, `UserService.findById(target_user_id=42)`에 `@PersonalDataAccess(fields={"email","phone"}, purpose="BUSINESS_INQUIRY")` 부착
**When** root가 `GET /api/v1/users/42` 호출
**Then** 200 + 사용자 상세 응답
**And** personal_data_access_log에 (viewer_id=1, target_user_id=42, accessed_fields=`["email","phone"]`, purpose='BUSINESS_INQUIRY', ip_address, user_agent, accessed_at=now) 1행 적재.

### M-002 — AOP 자동 적재 — 본인 조회 시 skip

**Given** 일반 사용자 'alice'(id=42) 인증
**When** alice가 `GET /api/v1/me` 호출 (내부적으로 UserService.findById(42), viewer_id == target_user_id)
**Then** 200 + 본인 정보 응답
**And** personal_data_access_log에 행이 적재되지 않는다 (REQ-AUTH-018-D-2 본인 조회 skip 규칙).

### M-003 — APPEND-ONLY 위반 — UPDATE 차단

**Given** personal_data_access_log에 행 1건 존재
**When** SUPER_ADMIN이 직접 SQL `UPDATE personal_data_access_log SET purpose='AUDIT' WHERE id=1` 실행
**Then** PostgreSQL 트리거가 `RAISE EXCEPTION 'personal_data_access_log is APPEND-ONLY (REQ-AUTH-018-D-3). UPDATE/DELETE blocked.'`로 거부
**And** 트랜잭션 ROLLBACK + audit_log에 (severity=CRITICAL, action='PDAL_MODIFICATION_ATTEMPT') 기록.

### M-004 — 관리자 검색 화면

**Given** SUPER_ADMIN 인증, personal_data_access_log에 100건 누적
**When** `GET /api/v1/admin/personal-data-access-log?targetUserId=42&from=2026-04-01&to=2026-04-30&fields=email&purpose=BUSINESS_INQUIRY&page=0&size=20` 호출
**Then** 200 + accessed_at 역순 페이징 결과
**And** 응답 content는 target_user_id=42 + 기간 + accessed_fields에 'email' 포함 + purpose='BUSINESS_INQUIRY' 모두 만족 행만 반환 (`accessed_fields ?| array['email']` GIN 인덱스 활용).

### M-005 — 본인 조회 권리 (REQ-AUTH-018-D-4)

**Given** alice(id=42) 인증, personal_data_access_log에 alice를 target으로 한 행 5건 (다른 관리자가 조회한 이력)
**When** alice가 `GET /api/v1/me/personal-data-access-log?from=2026-04-01&to=2026-04-30` 호출
**Then** 200 + 5건 모두 반환 (viewer_id, accessed_fields, purpose, accessed_at 노출 — 단, viewer의 개인정보는 username만 표시)
**And** 다른 사용자 'bob'을 target으로 한 행은 응답에 포함되지 않는다.

### M-006 — 콜드 이관 batch (REQ-AUTH-018-D-3)

**Given** personal_data_access_log에 accessed_at < now - 6개월인 행 1000건 존재
**When** 일일 archive batch 실행
**Then** 1000건이 personal_data_access_log_archive로 이관되고 (archived_at=now 적재)
**And** 원본 personal_data_access_log에서 해당 행 DELETE (트리거 우회 권한으로)
**And** audit_log에 batch 실행 이벤트 1건 (적재 건수, 실행 시각) 기록
**And** archive 쪽 partition은 월별로 분리되어 있다 (5년 보존 후 폐기 batch가 별도 운영).

---

## H-extra. 품질 게이트 추가 (Bundle A v0.3)

### QG-A-7 — RFP+홍익 비기능 (개인정보 접근 추적 100%)

**Given** v0.3 amendment 빌드 산출물에 대해 personal_data_access_log + verification_request 동작이 가능한 환경에서
**When** 다음 시나리오를 자동화로 검증하면
**Then** 모두 만족한다:
- 관리자 100명이 다른 사용자 100명을 조회하는 통합 테스트에서 personal_data_access_log에 정확히 100×100건의 행이 적재됨 (자기 조회 제외 — REQ-AUTH-018-D-2)
- AOP 어드바이스 누락 검출: `@PersonalDataAccess` 어노테이션 없는 user 정보 조회 메서드를 사용자가 호출 시 정적 분석 또는 통합 테스트에서 경고
- personal_data_access_log + archive 양쪽에 APPEND-ONLY 트리거가 활성 상태(SELECT * FROM pg_trigger WHERE tgname IN ('trg_pdal_no_update','trg_pdal_archive_no_update'))
- OTP code는 평문이 어떤 로그·DB 컬럼에도 저장되지 않음 (`SELECT count(*) FROM verification_request WHERE code_hash !~ '^\\$2[ab]\\$12\\$'` = 0, code_hash가 모두 BCrypt strength=12 형식)
- OTP 발송 응답 p95 < 3초 (NoOpSmsProvider 환경 < 100ms), OTP 검증 p95 < 200ms (k6 부하 테스트)
- IP 부정 시도(시간당 10회 초과) 검출 시 audit_log severity=CRITICAL 기록 100% 적용 (L-008 시나리오 자동화)
- 본인 조회 권리 API(`/api/v1/me/personal-data-access-log`)가 인증된 모든 사용자에게 동작 (M-005 시나리오 통과).

---

## L. Definition of Done (Bundle A 완료 기준, v0.3.1)

본 SPEC-CMS-002 v0.3.1은 다음을 모두 만족할 때 완료된 것으로 간주한다.

- v0.1 기준: 모든 REQ-AUTH-001-D ~ REQ-AUTH-012-D sub-requirement가 구현되고 acceptance.md A~G의 모든 시나리오가 자동화 테스트로 통과
- v0.2 추가: REQ-AUTH-013-D ~ REQ-AUTH-016-D sub-requirement가 구현되고 H~K의 모든 시나리오가 통과
- v0.3 추가: REQ-AUTH-017-D ~ REQ-AUTH-018-D sub-requirement가 구현되고 §L(본인인증) + §M(개인정보 접근 로그)의 모든 시나리오가 통과
- v0.3.1 운영 결정 Q-1 (사용자 2026-04-29): SMS 채널은 v0.4+ 후속. §L은 EMAIL 채널 기준으로 동작하며 L-001은 SMS 차단 검증, L-009는 SmsProvider placeholder 검증으로 갱신. SMS 정상 발송·재발송 쿨다운·어댑터 검증은 v0.4+ 활성화 시 재도입.
- QG-A-1~7의 7개 품질 게이트가 CI에서 모두 PASS (QG-A-6는 v0.2, QG-A-7은 v0.3 신규)
- Flyway V1 + V2 + V3 마이그레이션이 PostgreSQL 16에서 0 오류 순차 적용. V3의 verification_request `chk_vreq_channel` 제약은 EMAIL only.
- JaCoCo 커버리지: domain.auth + domain.organization + domain.verification + domain.privacy 패키지 line/branch 모두 ≥ 85%
- Vitest 커버리지: Admin SPA의 인증·권한 매트릭스·조직·본인인증·개인정보 접근 화면 컴포넌트 line ≥ 85%
- OpenAPI 3.1 스펙 자동 생성 후 Swagger UI에서 §6 + §13 + §16 신규 엔드포인트(`/auth/verify/request`, `/auth/verify/confirm`, `/admin/personal-data-access-log`, `/me/personal-data-access-log`) 모두 노출됨. `/auth/verify/request`의 channel 파라미터는 EMAIL만 enum 정의.
- 보안 담당자 검수: JWT/BCrypt/Refresh + 4단계 RBAC + 권한 변경 이력 + OTP 본인인증(EMAIL 채널 + SmsProvider placeholder) + 개인정보 접근 로그 정책 검증 완료 서명
- 운영 매뉴얼: 사용자·역할·잠금 해제·조직 트리·권한 매트릭스·OTP 본인인증(EMAIL)·개인정보 접근 추적·본인 조회 권리 절차가 한국어로 문서화됨. SMS 채널은 v0.4+ 후속 검토 안내 명시.

---

_문서 버전: v0.3.1 (2026-04-29 SMS 채널 v0.4+로 미룸 — 사용자 결정 Q-1 적용)_
_작성일: 2026-04-29_
_총 시나리오: A 8 + B 9 + C 13 + D 6 + E 10 + F 8 + G 4 + H 6 + I 5 + J 3 + K 5 + L 9 (L-001 SMS 차단 + L-002~L-008 EMAIL + L-009 placeholder; SMS 정상 발송 시나리오는 v0.4+ 후속) + M 6 + Quality Gate 7 = 99개_
