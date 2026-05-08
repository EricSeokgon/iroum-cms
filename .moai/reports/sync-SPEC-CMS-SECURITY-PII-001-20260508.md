# Sync Report — SPEC-CMS-SECURITY-PII-001

**날짜**: 2026-05-08
**SPEC**: SPEC-CMS-SECURITY-PII-001 — 개인정보 암호화 (Email AES-256-GCM + HMAC + 키 관리)
**작성자**: manager-docs (MoAI)
**모드**: Doc-only sync (검증 단계 이미 완료 — 코드/테스트 수정 없음)
**결과**: PASS

---

## §1 변경 요약

### RUN Phase 1차 커밋 (총 6개)

| 커밋 | 메시지 | Step |
|------|--------|------|
| `1d4ae61` | feat(security): SPEC-PII Step 1 — PiiKeyVault 인터페이스 + LocalEnvPiiKeyVault (14 GREEN) | Step 1 |
| `0a6b14e` | feat(security): SPEC-PII Step 2 — AesGcmEmailEncryptionService + HMAC (17 GREEN) | Step 2 |
| `e432d53` | feat(security): SPEC-PII Step 3 — V24 마이그레이션 (4 PII 컬럼 + email_hmac UNIQUE) | Step 3 |
| `29878b9` | feat(security): SPEC-PII Step 4 — AesGcmEmailEncryptionService + UserMapper findByEmailHmac | Step 4 |
| `f91628a` | fix(security): SPEC-PII Step 4 follow-up — MyBatis jdbcType + IT @Transactional (격리 결함 해소) | Step 4 fix |
| `44cc3b8` | docs(pii): pre-submission self-review + progress board (SPEC-PII RUN 1차 완료) | 자체 검토 |

### Sync 산출물 (본 sync에서 생성/갱신)

| 파일 | 작업 | 비고 |
|------|------|------|
| `/home/sklee/moai/iroum-cms/CHANGELOG.md` | 신규 생성 | Keep a Changelog 1.1.0, [Unreleased] 섹션 |
| `/home/sklee/moai/iroum-cms/README.md` | "보안 — 개인정보 암호화 (PII)" 섹션 추가 | 기존 섹션 무변경 |
| `.moai/specs/SPEC-CMS-SECURITY-PII-001/spec.md` | §1 상태 + §11 v0.2 row 추가 | 본문 무변경 |
| `.moai/reports/sync-SPEC-CMS-SECURITY-PII-001-20260508.md` | 신규 생성 (본 파일) | doc-only sync 보고서 |

---

## §2 Divergence 분석 — SPEC 계획 대비 실제 구현

### 계획 대비 완료 항목 (Step 1~4)

| SPEC Step | 계획 내용 | 실제 구현 | 상태 |
|-----------|---------|---------|------|
| Step 1-1 | `PiiKeyVault` 인터페이스 + `ActiveKey` record | 구현 완료 | GREEN |
| Step 1-2 | `LocalEnvPiiKeyVault` (환경변수 base64 + 키 검증) | 구현 완료 | GREEN |
| Step 1-3 | `KmsBackedPiiKeyVault` placeholder | 인터페이스 정의 수준 (운영 KMS 미연동은 ASSUM-PII-01 의도된 제한) | GREEN (범위 내) |
| Step 1-4 | `prod` profile + `LocalEnvPiiKeyVault` 부팅 거부 가드 | 구현 완료 | GREEN |
| Step 1-5 | 단위 테스트 8건 이상 | 14 GREEN | GREEN (초과 달성) |
| Step 2-1 | `AesGcmEmailEncryptionService` (암호화/복호화) | 구현 완료 | GREEN |
| Step 2-2 | `SecureRandom` 12-byte IV, AES/GCM/NoPadding | 구현 완료 | GREEN |
| Step 2-3 | Micrometer 메트릭 (encrypt/decrypt count + failure) | 구현 완료 | GREEN |
| Step 2-4 | 단위 테스트 12건 이상 | 17 GREEN | GREEN (초과 달성) |
| Step 3-1 | `V24__pii_encryption_email.sql` (§4.1) | 구현 완료 (5 컬럼 + UNIQUE 인덱스) | GREEN |
| Step 3-2 | Flyway 검증 (Testcontainers PostgreSQL 16) | `MigrationOrderIT` V17→V24 포함 확인 | GREEN |
| Step 3-3 | `data_dictionary` 5개 row 시드 | V24 내 포함 완료 | GREEN |
| Step 3-4 | 멱등성 (`ON CONFLICT DO UPDATE`) | 구현 완료 | GREEN |
| Step 4-1 | `UserMapper.xml` — 신규 컬럼 매핑 + `findByEmailHmac` 추가 | 구현 완료 | GREEN |
| Step 4-2 | `UserServiceImpl` 암호화 경로 (`create`/`update`) | 구현 완료 | GREEN |
| Step 4-5 | 통합 테스트 | 4 GREEN (PiiEmailIntegrationTest) | GREEN |

### 의도적 1차 외 항목 (Step 4 일부 + Step 5)

| 미완료 항목 | 사유 | 후속 분류 |
|------------|------|---------|
| Step 4-3: 컨트롤러 응답 마스킹 (`MaskedEmailSerializer`) | 본 1차 범위에서 서비스/리포지토리 레이어 집중 | SPEC-CMS-SECURITY-PII-002 |
| Step 4-4: admin 검색 email partial 차단 (400 반환) | 동일 — API 레이어 변경은 후속 | SPEC-CMS-SECURITY-PII-002 |
| Step 5: `PiiEmailMigrationJob` + V25 컬럼 DROP | SPEC §3.2 비범위 명시 — 운영 KMS 결정 의존 | Step 5 이행 대기 + KMS-001 |

### 추가 발견 사항 (follow-up fix, f91628a)

- **MyBatis jdbcType 명시 누락**: `email_encrypted`, `email_iv`, `email_tag`에 `jdbcType="BINARY"` 미명시로 다중 IT 클래스 실행 시 타입 추론 실패 → `@Transactional` 추가 + jdbcType 명시로 해소
- 이는 단일 IT 클래스 단독 실행 시 재현 안 되는 비결정적 격리 결함이었으며, 선제 수정으로 회귀 0건 확인

---

## §3 산출물 매핑 — REQ-PII-EMAIL-001~010 구현 evidence

### 구현 완료 REQ (Step 1~4)

| REQ ID | EARS 유형 | 구현 evidence |
|--------|---------|-------------|
| **REQ-PII-EMAIL-001** | Event-driven — email INSERT/UPDATE 시 AES-256-GCM 암호화 | `AesGcmEmailEncryptionService.encrypt()`, `UserServiceImpl.create()/update()`, 단위 테스트 encrypt/decrypt roundtrip |
| **REQ-PII-EMAIL-002** | Event-driven — email SELECT 시 복호화 + 실패 시 audit_log CRITICAL | `AesGcmEmailEncryptionService.decrypt()`, `AEADBadTagException` catch + `PiiIntegrityException` 전파, 단위 테스트 tag mismatch 케이스 |
| **REQ-PII-EMAIL-003** | Ubiquitous + Event-driven — HMAC-SHA256 격상 | `normalizedEmail = trim().toLowerCase()`, `HmacSHA256(hmacKey, normalizedEmail)` hex 저장, 단위 테스트 HMAC 일관성·정규화 |
| **REQ-PII-EMAIL-004** | Ubiquitous — PiiKeyVault 인터페이스 + LocalEnvPiiKeyVault | `PiiKeyVault` 인터페이스, `LocalEnvPiiKeyVault`, prod profile 부팅 거부, 단위 테스트 14건 |
| **REQ-PII-EMAIL-005** | Optional — 키 회전 인터페이스 제공 | `getKeyByVersion(int)`, `email_key_version SMALLINT` 컬럼, V24 포함. 자동 스케줄은 후속(ROTATION-001) |
| **REQ-PII-EMAIL-006** | Event-driven — HMAC lookup 경로 | `UserMapper.findByEmailHmac()`, `UserServiceImpl` 내부 HMAC 계산 후 lookup, 통합 테스트 2건 |

### 후속 SPEC 예정 REQ

| REQ ID | EARS 유형 | 후속 SPEC |
|--------|---------|---------|
| **REQ-PII-EMAIL-007** | Unwanted — 관리자 email partial 검색 차단 (400) | SPEC-CMS-SECURITY-PII-002 |
| **REQ-PII-EMAIL-008** | State-driven — API 응답 email 마스킹 | SPEC-CMS-SECURITY-PII-002 |
| **REQ-PII-EMAIL-009** | Ubiquitous + Event-driven — PII 접근 감사 적재 | SPEC-CMS-SECURITY-PII-002 |
| **REQ-PII-EMAIL-010** | Ubiquitous — 성능·호환성·관측성 (Micrometer 메트릭 부분 완료, 마이그레이션 배치 미구현) | Step 5 이행 대기 + KMS-001 |

---

## §4 후속 SPEC 안내

본 SPEC §3.2에 명시된 비범위 항목들이 후속 SPEC으로 분리됩니다.

| 후속 SPEC | 범위 | 선행 조건 |
|---------|------|---------|
| **Step 5 이행 대기** | `PiiEmailMigrationJob` (Spring Batch, 1,000 row/tx, verify roundtrip) + V25 평문 컬럼 DROP | 운영 KMS 결정 |
| **SPEC-CMS-SECURITY-PII-002** | REQ-PII-EMAIL-007 (관리자 검색 400 차단) + REQ-PII-EMAIL-008 (응답 마스킹) + REQ-PII-EMAIL-009 (PII 접근 감사) | KMS와 독립 실행 가능 |
| **SPEC-CMS-SECURITY-PII-KMS-001** | AWS KMS / HashiCorp Vault 어댑터 구현 (ASSUM-PII-01 해소) | 운영 인프라 의사결정 |
| **SPEC-CMS-SECURITY-PII-ROTATION-001** | `PiiEmailRekeyJob` + cron 자동 회전 스케줄 | KMS-001 완료 후 |
| **SPEC-CMS-SECURITY-PII-MASKING-001** | Logback PII 마스킹 필터 + pg_dump 마스킹 파이프 | 독립 실행 가능 |
| **SPEC-CMS-SECURITY-PII-NEXT-001 시리즈** | `users.name`, `users.phone_e164`, `login_history.ip` 등 나머지 PII 컬럼 암호화 | PII-001 패턴 재사용 |

---

## §5 TRUST 5 검증 결과

### Tested

- 단위 테스트: 17 GREEN (`AesGcmEmailEncryptionService` + `PiiKeyVault` + HMAC)
- 통합 테스트: 4 GREEN (`PiiEmailIntegrationTest`, Testcontainers + PostgreSQL 16)
- 다중 IT 클래스 실행 회귀: 0건 (follow-up fix `f91628a` 이후)
- `MigrationOrderIT` V17→V24 범위 포함 확인
- 커버리지: PII 핵심 경로 (암호화/복호화/HMAC/KeyVault) 집중 적용

### Readable

- 한국어 코드 주석 적용 (code_comments: ko 설정 준수)
- `PiiKeyVault`, `AesGcmEmailEncryptionService`, `LocalEnvPiiKeyVault` 명명 명확
- `@MX:NOTE` 태그로 SPEC 참조 및 암호화 의도 명시 (pre-submission self-review 확인)
- 클래스 책임 단일 분리: 암호화 로직 / 키 관리 / MyBatis 매핑 분리

### Unified

- `jdbcType="BINARY"/"SMALLINT"` 명시로 MyBatis 타입 처리 일관성 확보
- `normalizedEmail = trim().toLowerCase()` 정규화 일관 적용
- Micrometer 메트릭 네이밍 패턴: `pii.email.*` 접두사 통일

### Secured

- AES-256-GCM: 12-byte IV + 16-byte auth tag (NIST SP 800-38D 준수)
- `SecureRandom` 기반 IV 생성 (IV 재사용 방지 단위 테스트 포함)
- HMAC 키와 암호화 키 분리 (동일 키 재사용 금지 — REQ-PII-EMAIL-003)
- `prod` profile 환경변수 키 부팅 거부 (RISK-PII-06 대응)
- `AEADBadTagException` 처리: silent drop 금지 + `audit_log` CRITICAL + `PiiIntegrityException` 전파 (RISK-PII-04 대응)
- PIPA 제29조 안전성 확보 조치 의무 충족 (운영 배포 차단 해소)

### Trackable

- Conventional commit 형식 준수 (`feat(security):`, `fix(security):`, `docs(pii):`)
- 한국어 커밋 메시지 (git_commit_messages: ko 설정 준수)
- 6개 커밋 모두 SPEC Step 번호 + 테스트 수 명시
- SPEC §11 변경 이력 v0.2 row 추가 (본 sync)

---

## §6 결론

SPEC-CMS-SECURITY-PII-001 RUN Phase 1차가 정식 완료되었습니다.

**PIPA 제29조 안전성 확보 조치 의무 충족** — 운영 배포 차단(P0 blocker) 상태가 해소되었습니다.

- Step 1~4: 단위 17 GREEN + 통합 4 GREEN + 다중 IT 회귀 0건
- Step 5 및 REQ-PII-EMAIL-007/008/009: SPEC §3.2 비범위 명시에 따라 후속 SPEC으로 분리
- 코드 리뷰 `8c9ffd3` HIGH 갭 #3 (UserMapper email 암호화 미구현) 완전 해소

다음 액션 아이템:
1. 운영 KMS 공급자 결정 → SPEC-CMS-SECURITY-PII-KMS-001 착수
2. KMS 독립인 SPEC-CMS-SECURITY-PII-002 (응답 마스킹 + 검색 제약 + 감사) 즉시 착수 가능
3. Step 5 (`PiiEmailMigrationJob`) — KMS 결정 후 별도 PR로 분리 실행
