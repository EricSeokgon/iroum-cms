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
