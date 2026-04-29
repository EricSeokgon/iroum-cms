# SPEC-CMS-002 Research Notes

> 본 문서는 SPEC-CMS-002 (Bundle A — 회원·권한·로그인 상세) 작성 과정에서 검토한 기술적 의사결정과 근거를 기록한다.
> 결정 사항 자체는 spec.md에 명시되며, 본 문서는 사유와 대안 분석을 제공한다.
> 부모 SPEC: SPEC-CMS-001. 부모 연구 노트(`.moai/specs/SPEC-CMS-001/research.md`)의 §3 (JWT vs Session)·§6 (감사로그 AOP)·§7 (PIA) 결정을 본 SPEC에서 구현 단계 의사결정으로 확정한다.

---

## 1. JWT 라이브러리 선택 — jjwt vs java-jwt

### 의사결정

`io.jsonwebtoken:jjwt-api / jjwt-impl / jjwt-jackson` **0.12.x** 채택.

### 근거

- 0.12.x 버전은 Builder/Parser DSL이 매우 간결 (`Jwts.builder().subject(userId).expiration(exp).signWith(key).compact()`)
- Jakarta EE 9+ 호환, Spring Boot 3.x + JDK 17 검증 완료
- 서명 알고리즘 자동 협상 (HS256/RS256 모두 동일 API)
- `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` 검증 흐름이 직관적
- 활발한 유지보수 (2026년 시점 0.12.x가 최신 stable)

### 대안 검토

| 라이브러리 | 장점 | 거부 사유 |
|-----------|------|----------|
| `com.auth0:java-jwt` | 단순 API | 0.12.x jjwt 대비 boilerplate 많음, claim 빌드 verbose |
| `nimbus-jose-jwt` | JWE/JWK 풀 스펙 | 1차에 불필요, 학습 곡선 |
| Spring Security 6 OAuth2 Resource Server (`spring-security-oauth2-jose`) | Spring 통합 | Authorization Server 별도, 자체 발급 시 코드 비대화 |

### 구현 메모

- `JwtProperties` 빈에서 secret(env: `JWT_SECRET`), accessExpirationMinutes=15, refreshExpirationDays=7 로드
- `JwtTokenProvider` 컴포넌트가 발급·검증을 단일 진입점으로 노출
- HMAC-SHA256(HS256) 사용, secret은 256-bit 이상 강제 (jjwt가 길이 미달 시 예외)
- 향후 RS256 전환 가능성 대비, `SignatureAlgorithm` 설정 외부화

---

## 2. Refresh Token 저장 방식 — DB vs Redis

### 의사결정

**DB (`refresh_tokens` 테이블) 채택.** Redis는 1차 미도입.

### 근거

- 감사·관리 용이: 사용자별 활성 토큰 수, 회전 이력, 탈취 감지 이벤트를 SQL로 직접 조회·통계 산출
- 운영 인프라 단순화: PostgreSQL 1개로 영속성·트랜잭션 모두 해결, Redis 운영 비용·보안 검토 절감
- 강제 로그아웃·전체 로그아웃이 SQL UPDATE 한 줄로 즉시 반영
- 1차 출시 동시 접속자 규모(수백~수천)에서 token_hash UNIQUE 인덱스 조회 < 5ms로 충분
- 부모 SPEC research §3 결정과 일치 (Redis는 후속 캐시 최적화에서 도입)

### 대안 검토

| 옵션 | 장점 | 거부 사유 |
|------|------|----------|
| Redis (token_hash → user_id) | TTL 자동, 빠른 lookup | 추가 인프라, 감사·통계 SQL 불가, 영속성 보장 위해 RDB 백업 필요 |
| Stateless JWT Refresh (signed only) | DB 조회 불필요 | revoke 불가능 → 강제 로그아웃·탈취 감지 모두 구현 불가. 거부 |
| Spring Session + Redis | Spring 표준 | Stateless 원칙 위배, SPA·모바일 동일 인증 어려움 |

### 성능 검증 계획

- Testcontainers PostgreSQL 16에서 token_hash UNIQUE 인덱스 조회 벤치마크
- 활성 토큰 100,000건 수준에서 p95 < 5ms 확인
- 정리 batch (revoked_at IS NOT NULL OR expires_at < now): 일 1회, 100K 행 < 30초 목표

---

## 3. Refresh Token Rotation 정책

### 의사결정

**매 갱신 시 무조건 회전.** 기존 토큰은 즉시 revoked_at = now, revoke_reason='ROTATION'으로 표시. 폐기된 토큰의 재사용 시도 시 사용자 모든 활성 토큰을 일괄 폐기 (TOKEN_REUSE_DETECTED).

### 근거 (OWASP / Auth0 / RFC 6749 권고)

- 토큰 탈취 시 공격자와 정상 사용자 중 하나만 차기 회전에 성공 → 다른 한쪽이 다음 refresh 호출 시 폐기된 토큰을 사용 → 탈취 감지
- 감지 즉시 사용자 모든 토큰 폐기 → 공격자 세션 즉시 차단
- 정상 사용자는 한 번 강제 재로그인 필요 — UX 일부 희생, 보안 우선

### 구현 핵심

```
on /auth/refresh:
  hash = SHA256(cookie.refreshToken)
  row = SELECT * FROM refresh_tokens WHERE token_hash = hash
  if row IS NULL or expires_at < now:
    return 401
  if revoked_at IS NOT NULL:
    -- 탈취 감지
    UPDATE refresh_tokens SET revoked_at = now, revoke_reason='TOKEN_REUSE_DETECTED'
      WHERE user_id = row.user_id AND revoked_at IS NULL
    audit.critical("token_reuse_detected", userId=row.user_id)
    return 401
  -- 정상 회전
  UPDATE refresh_tokens SET revoked_at = now, revoke_reason='ROTATION' WHERE id = row.id
  newToken = randomUUID()
  INSERT refresh_tokens (token_hash = SHA256(newToken), user_id, expires_at, ip, ua)
  return 200 with new access + new cookie
```

### 트랜잭션 격리

- Refresh 핸들러 전체를 `SERIALIZABLE` 트랜잭션 또는 `SELECT ... FOR UPDATE`로 직렬화 검토
- 동시 다중 refresh 요청에서 race condition 방지 (양쪽 모두 같은 token을 정상으로 인식하는 일 방지)
- 1차는 `SELECT ... FOR UPDATE` 로우 락 채택 (READ COMMITTED 충분, 데드락 위험 낮음)

---

## 4. 비밀번호 해싱 — BCrypt vs Argon2id

### 의사결정

**BCrypt strength=12 채택.** Argon2id는 1차 미도입.

### 근거

- BCrypt strength=12는 표준 CPU에서 약 250ms로 brute-force 비용 충분 (OWASP 권고: ≥250ms)
- Spring Security 6 `BCryptPasswordEncoder` 즉시 사용 가능, 의존성 추가 없음
- BCrypt 해시 prefix(`$2a$12$` / `$2b$12$`)로 사이드 비교·migration 용이
- 부모 SPEC research §3 결정과 일치

### Argon2id 검토 결과

| 측면 | Argon2id | BCrypt 12 |
|------|----------|----------|
| 보안 강도 | 더 강함 (memory-hard) | 충분 (NIST FIPS 호환) |
| 구현 | `spring-security-crypto` Argon2 지원 | 기본 구현 |
| Spring 기본 | 미설정 | 설정 |
| PostgreSQL pgcrypto | 미지원 | crypt(text, gen_salt('bf', 12)) 지원 — 부수 호환 |
| 운영 사례 | 최신 권고이나 도입 사례 적음 | 광범위, 검증된 라이브러리 |

### 도입 보류 사유

- 1차 출시 일정 우선, BCrypt strength=12로 2026년 시점 보안 임계 충족
- Argon2 도입 시 메모리 파라미터(memory cost)·병렬도(parallelism cost) 튜닝·QA 비용 발생
- 후속 SPEC에서 password_hash 컬럼에 알고리즘 prefix 검출하여 점진 migration 가능 (`$argon2id$...` vs `$2a$12$...`)

### Spring Security 6 코드 예시 (참조)

`@Bean PasswordEncoder pe = new BCryptPasswordEncoder(12);` — strength=12로 명시 설정.

---

## 5. 메뉴별 권한 검사 구현 — @PreAuthorize + Caffeine 캐시

### 의사결정

- **메서드 레벨**: `@PreAuthorize("hasPermission('USER','READ')")` SpEL 표현식
- **권한 평가**: `PermissionEvaluator` 커스텀 빈에서 사용자 역할 → 권한 집합 매핑 검사
- **캐시**: Caffeine in-memory, key=userId, TTL=5분, max 10,000 entries

### 근거

- `@PreAuthorize`는 Spring Security 6 표준, AOP 기반으로 Controller 메서드 진입 시 자동 적용
- Caffeine은 Spring Boot 3 기본 캐시 매니저(`spring-boot-starter-cache`) 호환, 메모리 효율 우수
- 5분 TTL은 사용자 역할 변경 즉시성 vs DB 부하 사이 절충점
- 명시적 invalidate(역할 부여·회수 시)로 stale 시간 단축 가능

### 캐시 무효화 트리거

| 이벤트 | 캐시 키 |
|--------|---------|
| 사용자에 역할 부여 (POST /users/{id}/roles) | userId |
| 사용자에서 역할 회수 (DELETE /users/{id}/roles/{code}) | userId |
| 역할에 권한 매핑 변경 (POST /roles/{code}/permissions) | 해당 역할 보유 모든 user (전체 invalidate 또는 N+1 무효화) |
| 사용자 비활성화·삭제 | userId |

### 메뉴 단위 권한 매핑 — `menu_permissions` 테이블

- 메뉴 정의는 SPEC-CMS-004 (Bundle C)에서 `menu` 테이블로 관리됨
- 본 SPEC에서는 `menu_permissions(menu_id, permission_code)` 매핑 테이블만 schema 선언
- 메뉴 트리 조회 시 사용자 권한 집합과 INNER JOIN하여 접근 가능 메뉴만 반환
- 매핑이 없는 메뉴는 모든 인증 사용자 노출 (open menu — 1차 정책)

### 대안 검토

| 옵션 | 거부 사유 |
|------|----------|
| ACL 기반 (Spring Security ACL 모듈) | 데이터 인스턴스별 권한 — 본 SPEC은 리소스 타입별로 충분, 과한 복잡도 |
| OPA / OpenFGA 외부 권한 엔진 | 1차 인프라 단순화 우선, 외부 서비스 도입 부담 |
| Redis 기반 권한 캐시 | 단일 노드 1차 환경에서 in-memory로 충분, K8s 다중 노드 전환 시 검토 |

---

## 6. 사용자 ID 정책 — BIGINT vs UUID

### 의사결정

**Internal PK는 BIGINT IDENTITY.** UUID는 1차 미도입 (필요 시 향후 별도 컬럼).

### 근거

- egovframe 공통컴포넌트 v5의 SQL Mapper가 numeric ID 가정 (BIGINT 호환)
- BIGINT는 인덱스 크기·조인 성능에서 UUID 대비 우수 (8 bytes vs 16 bytes)
- 외부 노출 시 enumeration attack 위험은 BCrypt·Rate Limit·메뉴 권한으로 완화
- IDENTITY는 Oracle SEQUENCE+TRIGGER 패턴의 PostgreSQL 16 표준 대체

### UUID 도입 시점 (향후 검토)

- 외부 시스템 연동 (mobile app sync) 시 충돌 방지 필요 시
- 다기관 SaaS 멀티테넌시(SPEC-CMS-001 §3.2 비목표) 도입 시
- 그 시점에 `users.external_uuid VARCHAR(36) UNIQUE` 컬럼 추가 — internal id는 그대로 유지

### IDENTITY 정의

```sql
id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY
```

- `GENERATED ALWAYS`: 사용자가 명시적으로 id를 INSERT 시도해도 거부 (IDENTITY 무결성)
- `OVERRIDING SYSTEM VALUE` 키워드로 마이그레이션 시 강제 가능

---

## 7. 이메일 발송 — 비밀번호 재설정용

### 의사결정

**Spring Boot Mail Starter (`spring-boot-starter-mail`) + 외부 SMTP** 1차 도입.

### 근거

- 비밀번호 재설정은 Bundle A 핵심 기능, SMTP 미연동 시 재설정 자체 불가
- Q&A 답변 알림(SPEC-CMS-003 REQ-BOARD-008)은 부모 SPEC ASSUM-02에 따라 인앱 알림으로 우선 구현, SMTP 연동 시 활성화
- 발송 실패는 비동기 큐(`@Async`) + 재시도 (Spring Retry 3회) 후 audit_log 기록, 사용자에게는 "메일 확인 요청" 응답만 노출 (enumeration 방지)

### SMTP 옵션

| 옵션 | 비고 |
|------|------|
| 회사 메일 서버 (사내 SMTP relay) | 공공기관 표준, 1차 채택 권장 |
| SendGrid / Amazon SES | 빠른 도입, 과금 모델, 1차에 검토 가능 |
| 로컬 메일 서버 자체 운영 (Postfix) | 운영 부담 |

### 구현 메모

- `JavaMailSender` 빈, `SimpleMailMessage` 또는 `MimeMessageHelper` 사용
- 본문 템플릿: Thymeleaf 텍스트 모드 (`${resetUrl}` 치환)
- 발송 실패 시 재시도 큐: 향후 Redis Streams 또는 RabbitMQ로 확장 가능, 1차는 in-memory `@Async` ExecutorService

---

## 8. Brute-force 방어 — 다중 계층 Rate Limiting

### 의사결정

**3중 방어 채택**:
1. **계정별 잠금**: 5회 실패 → 30분 (REQ-AUTH-005-D-2)
2. **IP별 시간당 제한**: 동일 IP 시간당 30회 로그인 시도 (REQ-AUTH-005-D-6 / D-006)
3. **글로벌 비밀번호 정책**: 강력 비밀번호 강제 → password spray 자체 효과 감소

### IP 제한 라이브러리 — Bucket4j 채택

- `com.bucket4j:bucket4j-core` 8.x: token bucket 알고리즘 표준 구현
- Caffeine 기반 in-memory bucket 저장 (`Bucket4j.builder().withConfiguration(...)`)
- 분당 0.5 토큰 충전 + 버킷 30 → 1시간 30회 효과
- `HandlerInterceptor` 또는 Spring Security `OncePerRequestFilter`에서 적용

### 대안 검토

| 옵션 | 거부 사유 |
|------|----------|
| Spring Cloud Gateway RateLimiter | 1차에 별도 게이트웨이 미도입 |
| Resilience4j RateLimiter | API 단위 적용은 가능하나 IP·user 키 기반 분기 복잡 |
| Redis 기반 sliding window | Redis 의존성 추가, 1차 미도입 결정과 충돌 |
| nginx limit_req | L7 단순 차단은 가능하나 사용자별 정책·audit 연동 어려움 |

### Captcha (후속 SPEC 검토)

- reCAPTCHA v3 또는 hCaptcha 도입 시 IP 제한과 병행 가능
- 공공기관 외부 의존성 검토 필요 → 1차 미도입

---

## 9. 부모 SPEC 대비 변경 사항 — REQ-AUTH-010

### 변경 내역

부모 SPEC-CMS-001 §6.1 REQ-AUTH-010은 비밀번호 재사용 금지 범위를 **"직전 3회"** 로 명시한다. 본 상세 SPEC(REQ-AUTH-010-D-1)은 이를 **"직전 5개"** 로 확장한다.

### 변경 사유

- 90일 만료 정책(REQ-AUTH-004-D-3)과 결합 시, 사용자가 3개 비밀번호를 순환 사용해 사실상 만료 정책을 무력화할 수 있음 (3개를 매번 90일마다 회전)
- 5개로 확장 시 최소 약 15개월의 회전 주기 필요 → 무력화 곤란
- 산업 표준(NIST SP 800-63B 권고: 최근 1~10개 점검) 범위 내, 감사 대응에도 유리

### 영향 범위

- `password_history` 테이블 schema는 동일 (모든 변경 이력 저장)
- 검사 SQL의 `LIMIT` 만 3 → 5로 변경
- 부모 SPEC-CMS-001 인수기준 REQ-AUTH-010 G/W/T는 본 SPEC acceptance.md C-007/C-008로 강화 (5개 한도 검증 + 6번째 허용)

### 부모 SPEC 변경 제안

본 변경은 보안 강화 방향이며 부모 SPEC에 후속 amendment로 반영 권고. SPEC-CMS-001 §13 변경 이력에 v0.2 항목 추가 시 함께 반영.

---

_문서 버전: v0.1_
_작성일: 2026-04-29_
_본 SPEC의 결정 사항은 spec.md §4~§9, acceptance.md A~H에 반영되었다._

---

## 9. RFP 통합 결정 (v0.2 amendment, 2026-04-29)

본 절은 SPEC-CMS-001 v0.2 §15.2 SFR-014/SFR-010/SFR-015를 SPEC-CMS-002 v0.2 §13~§15에 반영하는 과정에서 검토한 의사결정과 근거를 기록한다. 결정 자체는 spec.md에 명시되며, 본 절은 사유와 대안 분석을 제공한다.

### 9.1 4단계 RBAC 도입 사유 (REQ-AUTH-013-D)

#### 의사결정

기존 v0.1의 3개 시스템 역할(SYSADMIN, CONTENT_ADMIN, USER)을 v0.2에서 4단계 표준(SUPER_ADMIN, DEPT_ADMIN, EDITOR, VIEWER)으로 확장한다. 단, v0.1 시드는 그대로 유지하고 v0.2 시드를 추가하는 비파괴 방식을 채택한다.

#### 근거

- RFP SFR-014 명시 요구: "최고/부서/Editor/Viewer 4단계 RBAC"
- 공공기관 운영 표준: 최고관리자 + 부서별 위임관리자 + 작성자 + 조회 사용자 패턴이 일반적
- v0.1의 SYSADMIN ⊃ CONTENT_ADMIN ⊃ USER는 위임 단계가 부족 — DEPT_ADMIN이라는 중간 위임 계층을 추가해 SUPER_ADMIN의 부담 분산
- VIEWER 역할은 외부 감사·점검 인력에게 부여할 수 있어 컴플라이언스 대응에 유리

#### 대안 검토

| 옵션 | 거부 사유 |
|------|----------|
| v0.1 3단계 유지 + RBAC 매핑만 보강 | RFP SFR-014 명시 요구 불충족, 외부 감사 시 표준 4단계 명칭 부재 |
| 5단계 (SUPER_ADMIN/DEPT_HEAD/DEPT_ADMIN/EDITOR/VIEWER) | 1차 출시 복잡도 증가, RFP 4단계 요건 초과 |
| ABAC(속성 기반) 도입 | 역할 위임 단순성 상실, OPA 등 외부 엔진 의존성 추가, 1차 미도입 |

#### 마이그레이션 전략

- v0.1 SYSADMIN 보유자는 자동으로 SUPER_ADMIN과 동등 권한 (alias)
- 운영 시 SUPER_ADMIN 사용 권장, SYSADMIN은 점진적 deprecation (별도 SPEC 검토)
- v0.2 신규 설치는 SUPER_ADMIN을 기본 시드로

### 9.2 organization 트리 모델 — Adjacency + Materialized Path

#### 의사결정

`organization` 테이블에 `parent_id`(Adjacency List)와 `path`(Materialized Path, `/1/3/12/`) 두 컬럼을 모두 보유한다. depth ≤ 5 제약은 CHECK constraint로 강제한다.

#### 근거

- Adjacency List 단독: 트리 깊이 검색이 재귀 CTE 필요 — 성능·복잡도
- Materialized Path 단독: 부모 변경 시 모든 후손 path 갱신 필요 — 쓰기 비용
- 두 모델 병행: 쓰기는 Adjacency 단순, 읽기(자기 부서 prefix LIKE)는 Path로 O(log n)
- depth ≤ 5: 공공기관 조직 트리 통상 본부 → 처 → 과 → 팀 → 셀(5단계)로 충분
- DEPT_ADMIN의 자기 부서 사용자 필터링이 핵심 사용 사례 — `users.organization_id` JOIN + `org.path LIKE '/1/3/%'`로 단일 인덱스 스캔

#### 대안 검토

| 옵션 | 거부 사유 |
|------|----------|
| Nested Sets (lft/rgt) | 삽입·삭제 시 전체 lft/rgt 갱신 비용 큼, 1차 운영 부담 |
| Closure Table 별도 | 추가 테이블 + 트랜잭션 복잡도, 5단계 깊이 제한이면 path로 충분 |
| LDAP 외부 디렉토리 위임 | 외부 의존성, 자체 프로젝트는 PostgreSQL 단일 책임 원칙 |

#### path 컬럼 갱신 전략

- 신규 INSERT 시: 부모 path + `/{new_id}/`
- parent_id 변경 시(부서 이전): 트리거 또는 application 서비스 layer에서 후손 path 일괄 UPDATE — 1차는 application layer 처리(트리거 디버깅 어려움)
- `idx_organization_path text_pattern_ops`로 prefix LIKE 검색 인덱스 가속

### 9.3 SSO Provider 인터페이스 — Strategy 패턴

#### 의사결정

`SsoProvider` 인터페이스 + `NoOpSsoProvider`(기본) + 자리표시자(`SamlSsoProvider`, `OidcSsoProvider`) 패키지 트리 보유. 1차 빌드는 `auth.sso.enabled=false`(기본)로 NoOp만 활성화.

#### 근거

- 자체 프로젝트는 JWT 자체 발급이 기본 (부모 SPEC §3.2 비목표)
- SFR-010은 비즈패스파인더 응찰 시나리오 한정으로 옵션화 (SPEC-CMS-001 v0.2 §16.1 SPEC-CMS-MIG-001 DEPRECATED 결정 반영)
- 그러나 미래 공공기관 통합로그인 도입 가능성에 대비해 인터페이스 자리표시자만 유지하면 후속 SPEC 작성 시 큰 리팩터링 없이 어댑터만 추가 가능
- Strategy 패턴 + Spring `@ConditionalOnProperty`는 1차 빌드에 SSO 코드가 포함되지 않도록 깔끔하게 분리

#### 대안 검토

| 옵션 | 거부 사유 |
|------|----------|
| 인터페이스 자체를 v0.2에서 제거 | 미래 SSO 도입 시 인증 흐름 전체 리팩터링 필요 |
| Spring Security `AuthenticationProvider` 직접 확장 | 일반 로그인과 결합 강해 옵션화·테스트 분리 어려움 |
| OAuth2 Resource Server (`spring-security-oauth2-jose`) 즉시 도입 | 1차 미요구, JWT 자체 발급 모델과 중복 |

#### 자리표시자 구현 노트

- `SamlSsoProvider`/`OidcSsoProvider`는 v0.2에 패키지·클래스 skeleton만 (모든 메서드 `throw UnsupportedOperationException`)
- 별도 SPEC(예: `SPEC-CMS-SSO-001`)에서 OpenSAML / Spring Security OAuth2 의존성 추가 후 실제 구현
- `auth.sso.enabled` 프로퍼티 + `auth.sso.provider` (saml|oidc)로 런타임 분기

### 9.4 권한 변경 이력 분리 사유 (audit_log 대비)

#### 의사결정

`audit_log`(REQ-CROSS-004)와 별개로 `permission_change_history` 전용 테이블을 신설한다. 두 테이블은 동일 트랜잭션에서 함께 INSERT된다 (이중 기록).

#### 근거

- audit_log는 모든 도메인 이벤트의 통합 로그 — 데이터 양이 매우 많음 (게시판·콘텐츠·시스템 모든 이벤트)
- 권한 컴플라이언스 보고는 "누가 누구에게 언제 어떤 권한을 부여·회수했는가"를 빠르게 검색 필요 — audit_log 전체 스캔은 비효율
- 전용 테이블에 `target_user_id`, `change_type`, `target_resource` 컬럼 인덱스 보유 시 검색 p95 < 50ms 달성 가능
- 분리하지 않으면 audit_log에 권한 변경만 필터링하는 복잡 쿼리 + JSONB 파싱 비용 발생
- 컴플라이언스 감사 시 권한 변경 이력만 별도 export 요구가 일반적 — 전용 테이블이 운영 편의

#### 트레이드오프

- 단점: 동일 정보 이중 저장 — 디스크 ~10% 추가 (권한 변경은 전체 이벤트 중 소수)
- 단점: 트랜잭션 일관성 유지 필요 — 동일 트랜잭션 내 양쪽 INSERT, 실패 시 롤백
- 장점: 검색·통계·보고 성능 + audit_log를 인증 도메인 외 이벤트로 단순화

#### 대안 검토

| 옵션 | 거부 사유 |
|------|----------|
| audit_log만 사용 + 인덱스 최적화 | 통합 로그의 행 수가 너무 많아 단일 인덱스로 한계 |
| audit_log View로 권한 변경만 추출 | View는 매번 base table 스캔 — 성능 미해결 |
| 외부 로그 시스템(예: Elasticsearch) 위임 | 1차 인프라 단순화 원칙과 충돌 |

#### change_type 분류 근거

- `GRANT/REVOKE`: 단일 권한 직접 부여·회수
- `ROLE_ASSIGN/ROLE_UNASSIGN`: 역할 매핑 추가·제거
- `PERM_ATTACH/PERM_DETACH`: 역할-권한 매핑 변경
- `DENIED_ATTEMPT`: REQ-AUTH-016-D-2 비인가 시도 차단 — 기존 audit_log critical과 별개로 권한 검색 화면에서 함께 보기 위한 분류

---

_문서 버전: v0.2 (2026-04-29 RFP 통합 amendment)_
_작성일: 2026-04-29_
_v0.2 amendment 결정 사항은 spec.md §13~§15, acceptance.md H~L에 반영되었다._

---

## 10. 홍익인간 CMS gap 통합 결정 (v0.3 amendment, 2026-04-29)

본 절은 SPEC-CMS-001 v0.2 §17 비기능 횡단 + 홍익인간 CMS gap analysis(2026-04-29)에서 식별된 본인인증·회원정보 접근 추적 요구를 SPEC-CMS-002 v0.3 §16~§17에 반영하는 과정에서 검토한 의사결정과 근거를 기록한다. 결정 자체는 spec.md에 명시되며, 본 절은 사유와 대안 분석을 제공한다.

### 10.1 OTP 길이 — 6자리 vs 8자리

#### 의사결정

**6자리 숫자 채택.** 8자리는 1차 미도입.

#### 근거

- 한국 시중은행·핀테크 표준이 6자리 (NH·KB·카카오뱅크 모두 6자리 SMS OTP)
- 6자리는 10^6 = 1,000,000 조합. 5분 만료 + 3회 시도 + IP 시간당 10회 차단 결합 시 무차별 대입 성공 확률 < 0.000003%로 수용 가능
- UX: 휴대폰 화면 → 입력 화면 전환 시 8자리는 외우기·재입력 부담이 약 30% 증가 (사용자 테스트 일반 결과)
- 5분 만료 정책으로 장기 유효성 위협이 제거되어 자릿수 추가 보안 이득이 미미

#### 대안 검토

| 옵션 | 거부 사유 |
|------|----------|
| 8자리 숫자 (10^8 조합) | UX 저하 대비 보안 이득 한계 (5분 만료·3회 차단으로 충분) |
| 6자리 영숫자 혼합 | 키패드 입력 어려움, 한국 사용자 친화성 저하 |
| TOTP (RFC 6238 인증 앱) | 1차는 SMS·EMAIL 채널 우선, TOTP는 후속 SPEC (2FA 도입 시) |

#### 보안 임계 보강

- BCrypt strength=12로 code_hash 저장 → 평문 OTP 추측 불가 (hash 비교 비용 ~250ms)
- 검증 시 timing attack 방지: `BCrypt.matches`는 상수 시간 비교 보장
- 5분 만료 단축 검토: 5분이 산업 표준, 더 짧으면 SMS 도달 지연 시 UX 저하

### 10.2 SMS 게이트웨이 — v0.4+ 후속 검토 (사용자 결정 2026-04-29 Q-1 적용)

#### 의사결정 (v0.3.1 갱신)

**1차 v0.3.1은 이메일 OTP만 유지하며, SMS 채널은 v0.4+ 후속 검토로 미룬다.** 본 절은 v0.4+ 활성화 시점의 우선 검토 어댑터 후보 기록 목적으로만 보존된다.

사용자 결정(2026-04-29 Q-1) 사유:
- 1차 운영 인력·외부 SMS 게이트웨이 계약·발신 번호 등록 절차 부담 제거
- 본인인증 흐름은 회원가입·비밀번호 재설정 위주이며 EMAIL 채널만으로도 1차 보안 임계 충족 (BCrypt(12) code_hash + 5분 만료 + 3회 시도 + IP 시간당 10회 차단)
- SMS 게이트웨이 도입은 운영 도메인이 안정화되고 SMS·알림톡 통합 알림 요건이 명확해진 이후 별도 SPEC(예: SPEC-CMS-SMS-001)으로 진행

#### v0.4+ 우선 검토 어댑터 후보 (참고만)

향후 SMS 채널 활성화 시 다음 후보 중 선정한다:

| 어댑터 | 한국 친화도 | 알림톡 연계 | 공공기관 사례 | 비고 |
|------|-----------|-----------|-------------|------|
| NHN Cloud Notification | 매우 높음 | 동일 콘솔 가능 | 다수 | v0.4+ 활성화 시 1순위 후보 (가격 8~10원/건, 한국 통신 3사 직접 연동, 카카오 알림톡 fallback) |
| Naver Cloud Platform SENS | 높음 | 가능 | 다수 | 2순위 후보 (NHN과 유사) |
| AWS SNS | 중간 | 불가 | 일부 | 한국 발신 번호 등록 절차 복잡, 보류 |
| Aligo | 중간 | 제한적 | 적음 | 공공기관 보안 검토·SLA 부족, 비추천 |
| 직접 통신사 연동 | 낮음 | — | — | 별도 계약·법인 인증 부담, 비추천 |

#### v0.4+ 활성화 시 작업 항목

- `SmsProvider` 인터페이스 시그니처 확정 (sendOtp + sendBulk 후보)
- 어댑터 skeleton 추가 (NhnCloud 우선)
- `auth.sms.provider` 프로퍼티 + `@ConditionalOnProperty` 분기 활성화
- `verification_request.chk_vreq_channel` 제약을 `(EMAIL)` → `(SMS,EMAIL)`로 확장 (Flyway 별도 마이그레이션)
- §16.1 REQ-AUTH-017-D-1 channel 검증 로직 SMS 허용
- acceptance.md L-001 SMS 차단 검증을 SMS 정상 발송 시나리오로 복원

#### v0.3.1 placeholder 구현 메모

- `SmsProvider` 인터페이스 정의만 패키지 트리에 둔다 (메서드 시그니처 v0.4+ 확정)
- `NoOpSmsProvider`만 default 빈으로 wired (SmsResult.success("noop-v0.3.1") 반환)
- NhnCloud/NaverCloud/AwsSns/Aligo 어댑터 skeleton은 v0.3.1에서 패키지 트리에 포함하지 않음
- 1차 본인인증 흐름은 SmsProvider를 호출하지 않으며 Spring Mail SMTP만 사용

### 10.3 personal_data_access_log 자동 적재 — AOP vs Repository 어드바이스

#### 의사결정

**Spring AOP `@PersonalDataAccess` 어노테이션 채택.** Repository advice는 1차 미도입.

#### 근거

- AOP advice는 메서드 시그니처에 영향 없이 횡단 관심사 분리 (REQ-CROSS-004 audit_log AOP와 동일 패턴 — 일관성)
- 어노테이션 메타로 `fields`·`purpose`를 메서드별로 명시 가능 → 동일 메서드라도 호출 컨텍스트별 분리 추적 어려움 회피
- Repository 어드바이스는 raw query 단위 적재 → 어떤 필드를 실제 응답에 포함했는지 알기 어려움 (raw select * 시 조회 필드 모호)
- AOP는 Service layer에 적용 → 비즈니스 의도(purpose)와 결합 자연스러움

#### 대안 검토

| 옵션 | 거부 사유 |
|------|----------|
| Repository advice (Spring Data 이벤트) | 비즈니스 의도(purpose) 결합 어려움, 응답 필드 명세 부정확 |
| Database trigger (PostgreSQL row-level audit) | viewer_id를 DB가 알 수 없음 (application context 필요), purpose 미부여 |
| Hibernate Interceptor | 영속성 layer 결합도 증가, 테스트 어려움 |
| 명시적 호출 (서비스 코드에 직접 INSERT) | 누락 위험 큼, 일관성 보장 어려움 |

#### 누락 검출 메커니즘

- QG-A-7: 정적 분석 또는 통합 테스트에서 user 정보 조회 메서드 중 `@PersonalDataAccess` 미부착 메서드를 검출해 경고
- 검사 도구: ArchUnit 또는 자체 스캐너 (`UserService` 패키지의 `findBy*` 메서드 모두 어노테이션 보유 확인)

#### 본인 조회 skip 사유

- viewer_id == target_user_id이면 자기 정보 열람 — GDPR/개인정보보호법상 추적 의무 없음 (자기결정권 행사)
- skip하지 않으면 매 본인 GET /me 호출마다 적재 → 무의미한 노이즈로 검색·보고 성능 저하
- 단, 본인이 "내 정보가 누구에 의해 조회되었는가"를 알 권리는 §M-005 본인 조회 API로 별도 보장

### 10.4 본인 접근 이력 본인 조회 — 사용자 권리 (GDPR Article 15 / 개인정보보호법 제35조)

#### 의사결정

**REQ-AUTH-018-D-4의 본인 조회 API(`GET /api/v1/me/personal-data-access-log`) 도입.** 인증된 모든 사용자에게 자신을 target으로 한 접근 이력 조회 권리 부여.

#### 근거 — 법적 근거

- GDPR Article 15 (Right of Access by the Data Subject): 정보주체는 자신의 개인정보를 처리하는 자에 대해 처리 사실·목적·기간·열람 이력 등을 알 권리 보유
- 개인정보보호법(KR) 제35조: 정보주체의 열람권 — "자신의 개인정보의 처리에 관한 정보의 열람을 요구할 수 있다"
- 동법 시행령 제41조: 개인정보 처리위탁·제3자 제공 이력 등을 정보주체 요구 시 제공
- 공공기관 컴플라이언스 감사에서 "정보주체 본인 조회 기능 부재" 지적사항이 빈번

#### 운영 정책

- 응답 형식: viewer 식별은 username만 노출 (full name·이메일은 마스킹 — 관리자 보호와 정보주체 권리 균형)
- 응답 컬럼: viewer_username, accessed_fields, purpose, accessed_at
- 응답 제외: viewer의 IP, user_agent (관리자 운영정보 보호)
- 페이징: 기본 size 20, 최대 100, 기간 필터 필수 권장

#### 대안 검토

| 옵션 | 거부 사유 |
|------|----------|
| 본인 조회 미제공 | GDPR/개인정보보호법 위반 위험, 컴플라이언스 감사 지적 |
| 본인 조회 제공 + viewer 정보 전체 노출 | 관리자 신원 보호·역공격 위험 (DEPT_ADMIN이 누구를 조회했는지 모든 사용자가 알면 사회공학적 공격 표적이 됨) |
| 사전 신청 후 수동 회신 | 자동화 부재, GDPR Article 12 "응답 시한 1개월" 운영 부담 |

### 10.5 OTP code 저장 — BCrypt vs 평문 vs HMAC

#### 의사결정

**BCrypt strength=12 채택.** 평문 저장은 즉시 거부, HMAC은 2순위 검토 후 거부.

#### 근거 — 위협 모델

- 위협 1 (DB 유출): 평문 저장 시 공격자가 진행 중인 모든 OTP를 즉시 사용 가능 → BCrypt로 무력화
- 위협 2 (백업 유출): 백업·로그·snapshot 어디에도 평문 OTP가 노출되지 않아야 함
- 위협 3 (재현 공격): 동일 code_hash가 같은 평문 OTP를 가리키지 않음(BCrypt는 매번 다른 salt) → rainbow table 공격 봉쇄

#### 대안 검토

| 옵션 | 거부 사유 |
|------|----------|
| 평문 저장 | 위협 1·2 모두 노출, 즉시 거부 |
| SHA-256 단순 해시 | 6자리 숫자는 사전 공격으로 < 1초 brute-force 가능 (10^6 시도) |
| HMAC-SHA256(secret, code) | secret 유출 시 동일 위협, 그리고 secret 회전 정책 부담 |
| BCrypt strength=10 | 비용 ~60ms로 빠르나, OTP 검증 자체는 사용자 1명·1회만 발생 → 강도 우선 (strength=12, ~250ms) |
| Argon2id | 더 강하나 §4 비밀번호 해싱 결정과 동일 사유로 1차 미도입 (BCrypt strength=12로 충분) |

#### 검증 흐름 (재현 어려움)

```
on /auth/verify/confirm:
  row = SELECT * FROM verification_request WHERE request_id = ?
  if row IS NULL or row.status != 'PENDING' or row.expires_at < now:
    return 401 VERIFY_CODE_EXPIRED or VERIFY_BLOCKED
  ok = BCrypt.matches(submitted_code, row.code_hash)
  if !ok:
    UPDATE verification_request SET attempts = attempts + 1
    if attempts + 1 >= max_attempts:
      UPDATE ... SET status = 'FAILED'
      return 423 VERIFY_BLOCKED
    return 401 VERIFY_CODE_INVALID
  UPDATE verification_request SET status = 'VERIFIED', verified_at = now
  INSERT verification_history (...)
  return 200 { verified: true }
```

#### 메모리 위생

- 평문 OTP는 발송 직후(`SmsProvider.sendOtp` 호출 직후) 변수에서 폐기
- 가능하면 `char[]` 사용 후 명시적 zero-fill (Java String immutability 한계로 BCrypt 입력 시점만 짧게 유지)
- BCrypt.hashpw 호출 후에는 hash 문자열만 보유

---

_문서 버전: v0.3 (2026-04-29 홍익인간 CMS gap 통합 amendment)_
_작성일: 2026-04-29_
_v0.3 amendment 결정 사항은 spec.md §16~§17, acceptance.md L~M + QG-A-7에 반영되었다._
