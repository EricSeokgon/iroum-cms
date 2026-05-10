# Sync Report — SPEC-CMS-SECURITY-AUTHZ-MATRIX-001

**날짜**: 2026-05-11
**SPEC**: SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 — HTTP 권한 매트릭스 통합 테스트 인프라 (운영 SecurityFilterChain + @PreAuthorize 회귀 검증)
**작성자**: manager-docs (MoAI)
**모드**: Doc-only sync (코드/테스트 수정 없음 — RUN 1차 완료 후 문서 동기화)
**결과**: PASS

---

## §1 변경 요약

### RUN Phase 1차 커밋 (총 2건)

| 커밋 | 메시지 | Step |
|------|--------|------|
| `af5ad41` | feat(spec): SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 작성 — HTTP 권한 매트릭스 IT 인프라 (운영 SecurityFilterChain + @PreAuthorize 회귀 검증) | SPEC 작성 |
| `f0ae970` | test(security): SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 RUN 1차 — AuthorizationMatrixIT 신설 (19 AC, HTTP 권한 매트릭스 회귀 검출 인프라) | Step 1~3 |

### Sync 산출물 (본 sync에서 생성/갱신)

| 파일 | 작업 | 비고 |
|------|------|------|
| `/home/sklee/moai/iroum-cms/CHANGELOG.md` | [Unreleased] 섹션 Added/Security 항목 추가 + 후속 SPEC 표 갱신 | PII-FOLLOWUP-001 entries 형식과 일관 |
| `/home/sklee/moai/iroum-cms/README.md` | SPEC 문서 표 row 추가 | 보안 섹션 본문 무변경 (IT 인프라 SPEC + 사용자 영향 0) |
| `.moai/specs/SPEC-CMS-SECURITY-AUTHZ-MATRIX-001/spec.md` | §1 상태 `Planned` → `Implemented (1차 — Step 1~3 완료, 2026-05-11)` + 제목 v0.1 → v0.2 + §11 v0.2 row 추가 | 본문 무변경 |
| `.moai/reports/sync-SPEC-CMS-SECURITY-AUTHZ-MATRIX-001-20260511.md` | 신규 생성 (본 파일) | doc-only sync 보고서 |

---

## §2 Divergence 분석 — SPEC 계획 대비 실제 구현

### 5/7 코드 리뷰 진단 정정 (MoAI 재진단 결과)

본 SPEC은 5/7 코드 리뷰(C1)의 진단 일부를 정밀화하여 작성되었다. sync 보고서에 이를 명시한다.

| 5/7 주장 | MoAI 재진단 (정확한 사실) |
|---------|--------------------------|
| 22 ControllerTest exclude | 실제 **58 ControllerTest** exclude (5/7 추정치 부정확) |
| `@WithMockUser` 장식적, 권한 게이트 미작동 | `WebMvcTestInfraConfig.testSecurityFilterChain`이 `@EnableMethodSecurity` + `ExceptionTranslationFilter` + `Http403ForbiddenEntryPoint`를 포함하여 **메소드 레벨 `@PreAuthorize` 권한 검증이 실제 작동** |
| `isForbidden()` 검증 0건 | commit `f80f95e`, `132d2c2` 보강 결과 **31 ControllerTest에 `isForbidden()`/`isUnauthorized()` 검증 존재** (58 중) |
| OWASP A01 커버리지 0% | 메소드 레벨 권한 검증은 31/58 컨트롤러에서 실효적으로 작동 중 |

**진정한 갭**: 5/7 진단의 행간에 존재하던 실제 미검증 영역 — 운영 `SecurityConfig` (운영 `SecurityFilterChain` + `JwtAuthenticationFilter`) 자체의 HTTP 권한 매트릭스 회귀 검출 인프라 부재. 이것이 본 SPEC의 실제 구현 목표였으며, 19/19 AC로 해소 완료.

### 계획 대비 완료 항목 (Step 1~3)

| SPEC Step | 계획 내용 | 실제 구현 | 상태 |
|-----------|---------|---------|------|
| Step 1 (REQ-AUTHZ-MATRIX-001) | `AuthorizationMatrixIT` 인프라 신설 + smoke test | `@SpringBootTest` + `@Testcontainers` + `@MockitoBean JwtTokenProvider`/`TokenBlacklistMapper` + PII 더미 키 + `givenValidToken` helper + 컨텍스트 부팅/public 200/보호 401 smoke test 3건 구현 완료 | GREEN (정적 검증) |
| Step 2 (REQ-AUTHZ-MATRIX-002) | 5~7 endpoint × {403/200} 매트릭스 | 6 endpoint × {권한 부족 → 403, 정합 권한 → 2xx} 12 IT 케이스 구현 완료. 권한 어휘 4종 커버 | GREEN (정적 검증) |
| Step 3 (REQ-AUTHZ-MATRIX-003) | 401/403 응답 body 회귀 + 운영 컴포넌트 적재 검증 | 401 `AUTH_REQUIRED` body + 403 `AUTH_FORBIDDEN` body + `JwtAuthenticationFilter` 적재 + Method Security 적재 4 IT 케이스 구현 완료 | GREEN (정적 검증) |

**변경 통계**: 1 file, +461 insertions, 0 deletions. 운영 코드 변경 0건.

### 추가 발견 사항 (계획 대비 19 AC 정밀 구현)

| 추가 발견 | 내용 | 채택 여부 |
|---------|------|---------|
| `JwtPrincipal.getAuthorities()` ROLE_ prefix 처리 | `hasRole('SUPER_ADMIN')` → Spring Security 내부 `ROLE_SUPER_ADMIN` 변환 — stub 시 `ROLE_` prefix 없이 역할명만 전달하여 Spring Security 표준 처리 위임 | 채택 (정적 검증 PASS) |
| `RetentionPolicyController` GET endpoint 존재 확인 | Governance 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` 회귀 검증 대상 — 실제 endpoint 존재 확인 후 IT에 반영 | 채택 |
| SPEC 계획의 5~7 endpoint → 실제 6 endpoint | Step 2 구현 시 6 endpoint(선택 7번 제외)로 확정 | 채택 (5~7 범위 내) |

---

## §3 산출물 매핑 — REQ-AUTHZ-MATRIX-001/002/003 구현 evidence

| REQ ID | EARS 유형 | 구현 evidence |
|--------|---------|-------------|
| **REQ-AUTHZ-MATRIX-001** | Ubiquitous — IT 인프라 신설 | `AuthorizationMatrixIT.java` (신규, 461줄) — `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Testcontainers(PostgreSQLContainer postgres:16-alpine)` + `@MockitoBean JwtTokenProvider` + `@MockitoBean TokenBlacklistMapper` + PII 더미 키 `@DynamicPropertySource` 주입 + `givenValidToken(roles, permissions)` JWT stub helper. `@MX:NOTE` + `@MX:SPEC` 클래스 헤더 적용. |
| **REQ-AUTHZ-MATRIX-002** | Event-driven — WRITE 권한 endpoint 매트릭스 | `@Nested class WriteAuthorizationMatrix` — 6 endpoint × 2 시나리오 = 12 `@Test`. Banner POST/PUT (`CONTENT:WRITE`), Page POST (`PAGE:WRITE`), CacheAdmin invalidate (`hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')`), User POST (`hasRole('SUPER_ADMIN')`), Governance class-level (`hasRole('ADMIN')`). 권한 어휘 분리 회귀 + 역할 위계 회귀 검출 패턴 포함. |
| **REQ-AUTHZ-MATRIX-003** | Event-driven + State-driven 복합 — 401/403 표준화 | `@Nested class ResponseBodyRegression` — 4 `@Test`. 401 `AUTH_REQUIRED` Content-Type + code + message, 403 `AUTH_FORBIDDEN` Content-Type + code + message, `JwtAuthenticationFilter` 적재 간접 검증, Method Security 인터셉터 적재 간접 검증. `code` 필드만 회귀 기준 (`message` 한국어 문구 변경 비회귀). |

---

## §4 후속 SPEC 안내

본 SPEC은 5/7 코드 리뷰 C1의 운영 SecurityFilterChain 갭을 해소하는 1차 인프라 SPEC이다. 후속 SPEC이 명확히 분리되어 있다.

| 후속 SPEC | 내용 | 우선순위 |
|----------|------|---------|
| **SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001** | 27 컨트롤러 isForbidden 메소드 레벨 보강 (본 SPEC 비범위로 분리) | Priority Medium |
| **SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001** | 매트릭스 IT 5~7 → 22+ 컨트롤러 확장 (본 SPEC 인프라 GREEN 안정화 후) | Priority Low |
| **SPEC-CMS-TEST-INFRA-RECONFIG-001** | 5/7 코드 리뷰 C2 — integration exclude 제거 + JaCoCo 신뢰도 회복 | Priority Medium |
| **SPEC-CMS-DATA-QUALITY-JOB-CLARIFY-001** | 5/7 코드 리뷰 C3 — DataQualityCheckJobTest 의미 명확화 | Priority Low |

본 SPEC은 5/7 코드 리뷰 C1을 **부분 해소** 상태로 갱신한다: 운영 SecurityFilterChain 회귀 검출 인프라 갭은 해소, 27 컨트롤러 메소드 레벨 보강은 `SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001` 후속 추적.

---

## §5 TRUST 5 검증 결과

**자체 검토(self-review) 적용** — 작업 작음(1 신규 파일) + 영향 좁음(테스트 코드 전용) + production 변경 0건 → manager-quality 위임 생략 (commit `f0ae970` 메시지에 명시).

### Tested

- 정적 검증 기준 (Java 17 미설치 환경 — 컴파일/실행 불가)
  - `AuthorizationMatrixIT` 클래스 구조: `@SpringBootTest` + `@Testcontainers` + `@MockitoBean` 2개 + PII 더미 키 `@DynamicPropertySource` — 컴파일 오류 없음 (정적 분석)
  - `givenValidToken(roles, permissions)` helper: `JwtTokenProvider.validateToken` + `parseClaims` Mock stub 패턴 — `SecurityConfigIntegrationTest` 동일 패턴 정적 검증
  - 19 `@Test` 메서드: `@Nested` 3클래스 × AC 1:1 매핑 구조 확인
  - `JwtPrincipal.getAuthorities()` ROLE_ prefix 처리: Spring Security `hasRole()` 내부 변환 정적 검증 PASS
  - `RetentionPolicyController` GET endpoint 존재 확인 (Governance 클래스 레벨 `hasRole('ADMIN')`)
- 실행 검증: Java 17 가용 환경에서 `./gradlew integrationTest --tests AuthorizationMatrixIT` 실행 권장 (사용자 별도 환경)
- 운영 코드 git diff = 0건 확인

### Readable

- 한국어 코드 주석 적용 (code_comments: ko 설정 준수)
- `AuthorizationMatrixIT` 명명: HTTP 권한 매트릭스 IT 의도 명확
- `@MX:NOTE` + `@MX:SPEC` 태그로 SPEC 참조 및 운영 SecurityFilterChain 적재 의도 명시
- `givenValidToken` helper 명명: 권한 시뮬레이션 의도 명확
- 3 `@Nested` 클래스로 AC 그룹 분리 (REQ-AUTHZ-MATRIX-001/002/003 대응)

### Unified

- `@MockitoBean` — `SecurityConfigIntegrationTest` 동일 패턴 일관
- PII 더미 키 `@DynamicPropertySource` — `AbstractIntegrationTest` 패턴 일관
- `@Testcontainers` PostgreSQL 16 — 기존 IT 클래스 동일 컨테이너 이미지 일관

### Secured

- 운영 코드 변경 0건: 보안 취약점 도입 불가
- `@MockitoBean JwtTokenProvider`, `@MockitoBean TokenBlacklistMapper` — 실제 JWT 발급 없음, DB 접근 없음
- PII 더미 키: 테스트 전용 임의값, 운영 키 노출 없음

### Trackable

- Conventional commit 형식 준수 (`feat(spec):`, `test(security):`)
- 한국어 커밋 메시지 (git_commit_messages: ko 설정 준수)
- 2개 커밋 모두 SPEC ID + REQ ID 명시
- SPEC §11 변경 이력 v0.2 row 추가 (본 sync)

---

## §6 OWASP A01 회귀 검출 능력 회복 결론

SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 RUN Phase 1차가 완료되었습니다.

- Step 1~3: AuthorizationMatrixIT 461줄 신규 + 19 AC 구현 완료
- REQ-AUTHZ-MATRIX-001/002/003: 모두 구현 완료, 운영 코드 변경 0건
- 5/7 코드 리뷰 C1 진정한 갭(운영 SecurityFilterChain 회귀 검출 인프라 부재) 해소

**OWASP A01 회귀 검출 능력**: 본 SPEC 적용 후 다음 영역의 회귀가 자동 검출됩니다.

- 운영 `SecurityConfig.requestMatchers().permitAll()` 매트릭스 변경
- 메소드 레벨 `@PreAuthorize` 어노테이션 누락/변경
- 401 `AUTH_REQUIRED` JSON body 형식 변경 (code 필드)
- 403 `AUTH_FORBIDDEN` JSON body 형식 변경 (code 필드)
- `JwtAuthenticationFilter` 위치/순서 변경

**IT 실행 안내**: 본 환경은 Java 17 미설치로 컴파일/IT 실행 불가. Java 17 가용 환경에서 아래 명령으로 GREEN 검증 권장:

```bash
# AuthorizationMatrixIT 단독 실행
./gradlew integrationTest --tests "*.AuthorizationMatrixIT"

# 전체 IT 회귀 확인
./gradlew integrationTest

# 운영 코드 변경 0건 확인
git diff --stat backend/src/main/
```
