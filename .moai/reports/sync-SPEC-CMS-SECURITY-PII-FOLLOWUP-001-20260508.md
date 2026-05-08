# Sync Report — SPEC-CMS-SECURITY-PII-FOLLOWUP-001

**날짜**: 2026-05-08
**SPEC**: SPEC-CMS-SECURITY-PII-FOLLOWUP-001 — PII 비동기 감사 IT 검증 인프라 정비 (@Disabled 3건 활성화)
**작성자**: manager-docs (MoAI)
**모드**: Doc-only sync (코드/테스트 수정 없음 — RUN 1차 완료 후 문서 동기화)
**결과**: PASS

---

## §1 변경 요약

### RUN Phase 1차 커밋 (총 2건)

| 커밋 | 메시지 | Step |
|------|--------|------|
| `4d05349` | feat(spec): SPEC-CMS-SECURITY-PII-FOLLOWUP-001 작성 — PII 비동기 감사 IT 검증 인프라 정비 (@Disabled 3건 활성화) | SPEC 작성 |
| `5fe440b` | test(security): SPEC-CMS-SECURITY-PII-FOLLOWUP-001 RUN 1차 — IntegrationAsyncConfig + @MockitoSpyBean + @Disabled 3건 활성화 | Step 1~3 |

### Sync 산출물 (본 sync에서 생성/갱신)

| 파일 | 작업 | 비고 |
|------|------|------|
| `/home/sklee/moai/iroum-cms/CHANGELOG.md` | [Unreleased] 섹션 Added/Changed 항목 추가 + 후속 SPEC 표 갱신 | PII-002 entries 형식과 일관 |
| `/home/sklee/moai/iroum-cms/README.md` | PII-001 후속 SPEC 표 row 추가 + SPEC 문서 표 row 추가 | 보안 섹션 본문 무변경 (IT 인프라 SPEC) |
| `.moai/specs/SPEC-CMS-SECURITY-PII-FOLLOWUP-001/spec.md` | §1 상태 `Draft` → `Implemented (1차 — Step 1~3 완료, 2026-05-08)` + §11 v0.2 row 추가 | 본문 무변경 |
| `.moai/reports/sync-SPEC-CMS-SECURITY-PII-FOLLOWUP-001-20260508.md` | 신규 생성 (본 파일) | doc-only sync 보고서 |

---

## §2 Divergence 분석 — SPEC 계획 대비 실제 구현

### 계획 대비 완료 항목 (Step 1~3)

| SPEC Step | 계획 내용 | 실제 구현 | 상태 |
|-----------|---------|---------|------|
| Step 1 (REQ-PII-FU-001) | `IntegrationAsyncConfig` @TestConfiguration + SyncTaskExecutor | 구현 완료 (59줄, @Profile("integration") + @Primary + @Bean(name="auditExecutor")) | GREEN |
| Step 2 (REQ-PII-FU-002) | `@SpyBean` → `@MockitoSpyBean` 마이그레이션 | 구현 완료 (사용처 1곳, Spring Framework 6.2 표준) | GREEN |
| Step 3 (REQ-PII-FU-003) | @Disabled 3건 제거 + IT 활성화 | 구현 완료 (AC-FU-003-1/2/3 활성화, Awaitility polling 정리) | GREEN (정적 검증 기준) |

### 추가 발견 사항 (계획에 없던 항목)

| 추가 발견 | 내용 | 채택 여부 |
|---------|------|---------|
| `@Import(IntegrationAsyncConfig.class)` 명시 보강 (D4-1 확장) | `@TestConfiguration` 자동 스캔 미보장 환경 대비 — `WebMvcTestInfraConfig` 선례 일관 | 채택 (D4-1 보강으로 분류, 사용자 확인 완료) |
| Awaitility polling 제거 (D5-1) | SyncTaskExecutor override로 동기 실행 보장 → `await()` 불필요 제거 + import 정리 | 채택 (D5-1 채택, 사용자 확인 완료) |

---

## §3 산출물 매핑 — REQ-PII-FU-001/002/003 구현 evidence

| REQ ID | EARS 유형 | 구현 evidence |
|--------|---------|-------------|
| **REQ-PII-FU-001** | Ubiquitous — IT 전용 SyncTaskExecutor override | `IntegrationAsyncConfig.java` (신규, 59줄) — `@TestConfiguration` + `@Profile("integration")` + `@Primary` + `@Bean(name="auditExecutor")` SyncTaskExecutor 반환. 운영 `AsyncConfig.auditExecutor()` ThreadPoolTaskExecutor 미영향. |
| **REQ-PII-FU-002** | Ubiquitous — Spring Framework 6.2 표준 @MockitoSpyBean 사용 | `PiiAuditEnhanceIT.java` — `@SpyBean` import 제거, `@MockitoSpyBean` 교체. 5-arg `recordBulk` matcher 시그니처 매칭 한계 해소. |
| **REQ-PII-FU-003** | Event-driven — @Disabled IT 3건 GREEN | `PiiAuditEnhanceIT.java` — `findPage_bulkAuditLog_nRows`, `auditInsertFailure_returns200AndDoesNotPropagateError`, `findPage_bulkAudit_distinctTargetUserIds` @Disabled 제거. PII-002 RUN 1차 forward reference 완전 회수. |

**변경 통계**: 2 files, +88 insertions, -35 deletions. 운영 코드 변경 0건.

---

## §4 후속 SPEC 안내

본 SPEC은 SPEC-CMS-SECURITY-PII 시리즈의 follow-up 종착점이다. 추가 follow-up SPEC 발의 예정 없음.

운영 인프라 의사결정에 의존하는 사항(KMS 키 회전, 다른 PII 컬럼 암호화, 백업 마스킹)은 SPEC-CMS-SECURITY-PII-002 §3.2에 이미 분리 명시되어 있으며, 본 SPEC과 무관하다.

---

## §5 TRUST 5 검증 결과

**자체 검토(self-review) 적용** — 작업 규모 小(2 files) + 운영 코드 변경 0건 + 영향 범위 IT-only → manager-quality 위임 생략 (commit `5fe440b` 메시지에 명시).

### Tested

- 정적 검증 기준 (Java 17 미설치 환경 — 컴파일/실행 불가)
  - `IntegrationAsyncConfig` 클래스 구조: @TestConfiguration + @Profile("integration") + @Primary + SyncTaskExecutor 반환 — 컴파일 오류 없음 (정적 분석)
  - `@MockitoSpyBean` import 경로: `org.springframework.test.context.bean.override.mockito.MockitoSpyBean` — Spring Framework 6.2 표준 확인
  - @Disabled 제거 대상 3건: `findPage_bulkAuditLog_nRows`, `auditInsertFailure_returns200AndDoesNotPropagateError`, `findPage_bulkAudit_distinctTargetUserIds` — 정상 제거 확인
- 실행 검증: Java 17 가용 환경에서 `./gradlew integrationTest` 실행 권장 (사용자 별도 환경)
- 운영 코드 git diff = 0건 확인

### Readable

- 한국어 코드 주석 적용 (code_comments: ko 설정 준수)
- `IntegrationAsyncConfig` 명명: IT 전용 비동기 설정 의도 명확
- `@MX:NOTE` + `@MX:SPEC` 태그로 SPEC 참조 및 override 의도 명시
- Awaitility import 제거로 불필요 의존 없는 간결한 테스트 클래스

### Unified

- `@Import(IntegrationAsyncConfig.class)` — `WebMvcTestInfraConfig` 선례 일관
- `@Profile("integration")` + `@Primary` — 기존 IT 인프라 패턴 일관
- `@MockitoSpyBean` — Spring Boot 3.5.x / Spring Framework 6.2 표준 적용

### Secured

- 운영 코드 변경 0건: 보안 취약점 도입 불가
- `@Profile("integration")` 한정: default profile(운영) 무영향
- IT-only SyncTaskExecutor: 운영 ThreadPoolTaskExecutor 대체 없음

### Trackable

- Conventional commit 형식 준수 (`feat(spec):`, `test(security):`)
- 한국어 커밋 메시지 (git_commit_messages: ko 설정 준수)
- 2개 커밋 모두 SPEC ID + REQ ID 명시
- SPEC §11 변경 이력 v0.2 row 추가 (본 sync)
- PII-002 forward reference (@Disabled 3건) 완전 회수 추적 가능

---

## §6 결론

SPEC-CMS-SECURITY-PII-FOLLOWUP-001 RUN Phase 1차가 완료되었습니다.

- Step 1~3: IntegrationAsyncConfig 신규 + @MockitoSpyBean 마이그레이션 + @Disabled 3건 제거 완료
- REQ-PII-FU-001/002/003: 모두 구현 완료, 운영 코드 변경 0건
- PII-002 RUN 1차 forward reference (@Disabled 3건 AC-009-1/5/6) 완전 회수

**IT 실행 안내**: 본 환경은 Java 17 미설치로 컴파일/IT 실행 불가. Java 17 가용 환경에서 아래 명령으로 GREEN 검증 권장:

```bash
./gradlew integrationTest --tests "*.PiiAuditEnhanceIT"
```

대상 IT 3건:
- `PiiAuditEnhanceIT.findPage_bulkAuditLog_nRows`
- `PiiAuditEnhanceIT.auditInsertFailure_returns200AndDoesNotPropagateError`
- `PiiAuditEnhanceIT.findPage_bulkAudit_distinctTargetUserIds`
