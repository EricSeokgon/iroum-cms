# SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002: HTTP 권한 매트릭스 IT 확장 2차 — ArchUnit baseline 31 어휘 100% IT 커버 v0.2

**Status**: Diagnosed (2026-05-12) — endpoint 정밀 매핑 완료, Step 2 RUN 진입 준비됨
**Trigger**: AUTHZ-AUTODETECT-001 Step 2 GREEN 확정으로 ArchUnit 운영 실측 권한 어휘 31종 노출 + IT 매트릭스 12 어휘 커버 갭 19종 식별
**Severity**: P2 (보안 회귀 검출 능력 확대, 운영 영향 0)

---

## v0.2 변경 이력 (2026-05-12) — Step 2 endpoint 정밀 진단 완료

### Explore subagent 정밀 매핑 결과

19 어휘 × 운영 컨트롤러 14개 정밀 grep 후 정확한 HTTP method + path 추출 완료. Step 2 Phase A 13 RED revert (commit `afe06fb`) 원인 해소.

### 정확한 Endpoint 매핑 표 (실측 24 endpoint)

| # | 어휘 | Controller | HTTP | Full Path | @PreAuthorize | OR bypass |
|---|------|-----------|------|-----------|---------------|-----------|
| 1 | ROLE:CONTENT_ADMIN | QnaController | POST | /api/v1/qnas/{id}/answer | hasAnyRole('CONTENT_ADMIN','ADMIN','SUPER_ADMIN') | **ADMIN, SUPER_ADMIN** |
| 2 | CONTENT:READ | I18nController | GET | /api/v1/content/i18n | hasAuthority('CONTENT:READ') | 없음 |
| 2-1 | CONTENT:READ | PopupController | GET | /api/v1/content/popups | hasAuthority('CONTENT:READ') | 없음 |
| 3 | PAGE:READ | ContentBlockController | GET | /api/v1/content/pages/{pageId}/blocks | hasAuthority('PAGE:READ') | 없음 |
| 4 | PAGE:ROLLBACK | PageController | POST | /api/v1/content/pages/{id}/rollback/{version} | hasAuthority('PAGE:ROLLBACK') | 없음 |
| 5 | PAGE:HISTORY:READ | PageController | GET | /api/v1/content/pages/{id}/history | hasAuthority('PAGE:HISTORY:READ') | 없음 |
| 6 | SITE:WRITE | SiteController | PUT | /api/v1/content/sites/{id} | hasAuthority('SITE:WRITE') | 없음 |
| 7 | MENU:PERMISSION:WRITE | MenuController | POST | /api/v1/content/menus/{id}/permissions | hasAuthority('MENU:PERMISSION:WRITE') | 없음 |
| 8 | TEMPLATE:READ | TemplateController | GET | /api/v1/content/templates | hasAuthority('TEMPLATE:READ') | 없음 |
| 8-1 | TEMPLATE:READ | TemplateController | GET | /api/v1/content/templates/{id} | hasAuthority('TEMPLATE:READ') | 없음 |
| 9 | USER:READ | PersonalDataAccessController | GET | /api/v1/audit/personal-data-access | AUDIT:READ **AND** USER:READ | 없음 (AND 조건) |
| 10 | SYSTEM:DASHBOARD | DashboardController | GET | /api/v1/system/dashboard/kpi | hasAuthority('SYSTEM:DASHBOARD') | 없음 |
| 11 | SYSTEM:READ | SeoRedirectController | GET | /api/v1/content/seo/redirects | hasAuthority('SYSTEM:READ') | 없음 |
| 12 | SYSTEM:SETTING:READ | SystemSettingController | GET | /api/v1/system/settings | hasAuthority('SYSTEM:SETTING:READ') | 없음 |
| 12-1 | SYSTEM:SETTING:READ | SystemSettingController | GET | /api/v1/system/settings/{key} | hasAuthority('SYSTEM:SETTING:READ') | 없음 |
| 13 | SYSTEM:SETTING:WRITE | SystemSettingController | PUT | /api/v1/system/settings/{key} | hasAuthority('SYSTEM:SETTING:WRITE') | 없음 |
| 14 | SYSTEM:MAINT:READ | MaintenanceController | GET | /api/v1/system/maintenance | hasAuthority('SYSTEM:MAINT:READ') | 없음 |
| 14-1 | SYSTEM:MAINT:READ | MaintenanceController | GET | /api/v1/system/maintenance/{id} | hasAuthority('SYSTEM:MAINT:READ') | 없음 |
| 15 | SYSTEM:MAINT:WRITE | MaintenanceController | POST | /api/v1/system/maintenance | hasAuthority('SYSTEM:MAINT:WRITE') | 없음 |
| 15-1 | SYSTEM:MAINT:WRITE | MaintenanceController | POST | /api/v1/system/maintenance/{id}/activate | hasAuthority('SYSTEM:MAINT:WRITE') | 없음 |
| 16 | SYSTEM:LOG:READ | AccessLogController | GET | /api/v1/system/access-logs | hasAuthority('SYSTEM:LOG:READ') | 없음 |
| 17 | SYSTEM:ADMIN | SiteController | POST | /api/v1/content/sites | hasAuthority('SYSTEM:ADMIN') | 없음 |
| 17-1 | SYSTEM:ADMIN | SeoRedirectController | POST | /api/v1/content/seo/redirects | hasAuthority('SYSTEM:ADMIN') | 없음 |
| 17-2 | SYSTEM:ADMIN | SeoRedirectController | DELETE | /api/v1/content/seo/redirects/{id} | hasAuthority('SYSTEM:ADMIN') | 없음 |
| 18 | AUDIT:READ | PermissionChangeController | GET | /api/v1/audit/permission-changes (class-level) | hasAuthority('AUDIT:READ') [class-level] | 없음 |
| 18-1 | AUDIT:READ | LoginHistoryController | GET | /api/v1/audit/login-history (class-level) | hasAuthority('AUDIT:READ') [class-level] | 없음 |

### SecurityConfig 정책 (Explore 확인)

- **permitAll**: /api/v1/health/*, /api/v1/auth/*, /v3/api-docs/*, /swagger-ui/*, /actuator/health|info|backupStatus, GET /api/v1/boards/**, GET /api/v1/faqs/**, GET /api/v1/publications/**, POST /api/v1/publications/*/download-zip, GET /api/v1/surveys/**, POST /api/v1/surveys/*/responses, GET /api/v1/search*, POST /api/v1/search/click
- **authenticated()**: 기타 모든 endpoint
- **JWT Filter**: UsernamePasswordAuthenticationFilter 앞 배치, STATELESS

### Phase A 13 RED revert 원인 확정

1. **HTTP method 부정확** — POST/PUT/GET 혼동으로 404 발생 (특히 SITE:WRITE는 PUT, SYSTEM:MAINT:WRITE는 POST)
2. **path 부정확** — {pageId}/blocks vs {id}/blocks 등 path variable 오류
3. **USER:READ 단독 endpoint 부재** — AUDIT:READ AND USER:READ AND 조건만 운영 (USER:READ 단독 시나리오 작성 시 403 fall-through)
4. **클래스 레벨 @PreAuthorize** — PermissionChangeController/LoginHistoryController는 클래스 레벨, 메소드 path 별도 확인 필요

### Step 2 RUN 진입 준비 완료

SPEC §8 "다음 세션 RUN 진입 전 필수 작업" #1~#3 완료:
- ✅ 각 운영 endpoint 정밀 검증 (24 endpoint 확정)
- ✅ 운영 권한 정책 OR 조건 확인 (QnaController만 ADMIN/SUPER_ADMIN bypass)
- ✅ endpoint 매핑 표 정확화 (위 표)

다음 작업 (#4): Step 2 시나리오 작성 시 위 24 endpoint 1개씩 단위 검증 → 확정 후 일괄 추가

---

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002 |
| 제목 | HTTP 권한 매트릭스 IT 확장 2차 (12 → 31 어휘 100% 커버) |
| 우선순위 | P2 |
| 분류 | Cross-cutting Security IT Coverage Expansion (2차) |
| 의존 | AUTHZ-IT-EXPAND-001 (Implemented 1차), AUTHZ-AUTODETECT-001 (Implemented 1차) |
| 형제 | AUTHZ-IT-EXPAND-003 (가칭, 120 endpoint 전체) |

---

## 2. 배경 및 동기

### 2.1 AUTHZ-IT-EXPAND-001 + AUTHZ-AUTODETECT-001 산출물 결합

- **AUTHZ-IT-EXPAND-001 v0.2 Implemented**: 12 권한 어휘 × 29 endpoint × 3 시나리오 = 88 AC
- **AUTHZ-AUTODETECT-001 v0.2 Implemented**: ArchUnit으로 운영 권한 어휘 **31종** 자동 발견 + baseline 회귀 검출

### 2.2 잔여 갭 (본 SPEC 검증 대상)

| 카테고리 | AUTHZ-IT-EXPAND-001 커버 | ArchUnit baseline 운영 실측 | 미커버 |
|----------|----------|----------|----------|
| 어휘 수 | 12 | 31 | **19** |
| endpoint 수 (대표) | 29 | 35+ | ~20+ |
| 분리 회귀 검증 | 5건 | (확장) | - |

### 2.3 미커버 19 권한 어휘 (운영 실측, 본 SPEC 대상)

| # | 어휘 | 도메인 | 운영 컨트롤러 |
|---|------|--------|---|
| 1 | `ROLE:CONTENT_ADMIN` | Auth/Content | 4 파일 |
| 2 | `CONTENT:READ` | Content | 3 파일 |
| 3 | `PAGE:READ` | Content | 1 파일 (Page) |
| 4 | `PAGE:ROLLBACK` | Content | 1 파일 (Page) |
| 5 | `PAGE:HISTORY:READ` | Content | 1 파일 (Page) |
| 6 | `SITE:WRITE` | Content | 1 파일 (Site) |
| 7 | `MENU:PERMISSION:WRITE` | Content | 1 파일 (Menu) |
| 8 | `TEMPLATE:READ` | Content | 1 파일 (Template) |
| 9 | `USER:READ` | Auth | 1 파일 (User) |
| 10 | `SYSTEM:READ` | System | 1 파일 (System) |
| 11 | `SYSTEM:DASHBOARD` | Dashboard | 1 파일 |
| 12 | `SYSTEM:SETTING:READ` | System | 1 파일 (Setting) |
| 13 | `SYSTEM:SETTING:WRITE` | System | 1 파일 (Setting) |
| 14 | `SYSTEM:MAINT:READ` | System | 1 파일 (Maint) |
| 15 | `SYSTEM:MAINT:WRITE` | System | 1 파일 (Maint) |
| 16 | `SYSTEM:LOG:READ` | System | 1 파일 (Log) |
| 17 | `SYSTEM:ADMIN` | System | 2 파일 |
| 18 | `AUDIT:READ` | Governance | 3 파일 |
| 19 | (예비) | - | - |

OWASP A01 회귀 검출 능력 완전 확보 — IT 매트릭스가 ArchUnit baseline의 100%를 커버.

---

## 3. 범위 + 비범위

### 3.1 범위 (P2)

| REQ | 설명 |
|-----|------|
| **REQ-AM-EXP2-001** | `AuthorizationMatrixExpand2IT` 신설 (또는 기존 ExpandIT 확장) — 19 권한 어휘 × 평균 2 endpoint × 3 시나리오 = ~114 AC |
| **REQ-AM-EXP2-002** | 19 어휘 운영 endpoint 정밀 매핑 (각 어휘별 최소 1 endpoint) |
| **REQ-AM-EXP2-003** | 어휘 분리 회귀 검증 강화 (READ vs WRITE, ROLLBACK vs HISTORY:READ 등 새로운 분리 패턴) |
| **REQ-AM-EXP2-004** | `AuthorizationCoverageArchTest` baselineEndpoints 갱신 (35 → 50+ endpoint) — IT 커버 endpoint 확대 동기화 |

### 3.2 비범위

- 운영 코드 변경 (IT 신설/확장 전용)
- 120 endpoint 전체 적용 (AUTHZ-IT-EXPAND-003 영역)
- 메소드 슬라이스 IT (@WebMvcTest) — CTRL-AUTHZ-COVERAGE-001 영역

---

## 4. 사용자 결정 (다음 세션 사전 결정 필요)

| 결정 | 옵션 | 권장 |
|------|------|------|
| **D1** 테스트 클래스 구조 | (a) AuthorizationMatrixExpandIT 확장 / (b) 신규 AuthorizationMatrixExpand2IT 분리 | (b) — 1540줄 + ~600줄 = 2100줄 폭발 방지 |
| **D2** 도메인 그룹화 | 7 도메인 (기존 패턴 유지) | 유지 |
| **D3** 분리 회귀 검증 범위 | READ vs WRITE, ROLLBACK vs HISTORY:READ, SYSTEM:* 계열 분리 | 모두 포함 |
| **D4** baseline endpoint 갱신 시점 | (a) Step별 점진 / (b) 마지막 일괄 | (a) 점진 — 각 도메인 활성화 시 baseline 갱신 |

---

## 5. EARS 요구사항

### REQ-AM-EXP2-001 (Ubiquitous) — 19 어휘 IT 매트릭스 신설

**EARS**: "The system SHALL provide HTTP authorization matrix integration test scenarios for all 19 operational `@PreAuthorize` vocabularies not yet covered by AUTHZ-IT-EXPAND-001, achieving 100% coverage of the 31 vocabularies in ArchUnit baseline."

세부:
- 19 어휘 × 평균 2~3 endpoint × 3 시나리오 ≈ 100~150 AC
- 새 `AuthorizationMatrixExpand2IT.java` 신설 (~800~1000줄 예상)
- AUTHZ-MATRIX-001 + EXPAND-001 + EXPAND-002 = 3중 IT 매트릭스로 OWASP A01 완전 커버

### REQ-AM-EXP2-002 (Event-driven) — 어휘별 endpoint 정밀 매핑

**EARS**: "When a new vocabulary in ArchUnit baseline is added, the system SHALL identify at least one representative endpoint per vocabulary and add corresponding IT scenarios."

### REQ-AM-EXP2-003 (Ubiquitous) — 어휘 분리 회귀 검증

**EARS**: "The system SHALL include separation regression tests for vocabularies that share namespace prefixes (e.g., PAGE:READ vs PAGE:WRITE, SYSTEM:SETTING:READ vs SYSTEM:SETTING:WRITE) to detect accidental vocabulary substitution."

### REQ-AM-EXP2-004 (Event-driven) — ArchUnit baseline 동기화

**EARS**: "When IT scenarios are added in EXPAND-002, the `AuthorizationCoverageArchTest.baselineEndpoints()` SHALL be updated to include the new endpoints, maintaining 100% match between IT @DisplayName extraction and baseline set."

---

## 6. RUN Step 분해 (다음 세션)

### Step 1: 19 어휘 endpoint 정밀 매핑 + AuthorizationMatrixExpand2IT 신설
- 각 어휘별 운영 컨트롤러 grep으로 endpoint 정확 매핑
- 새 IT 클래스 신설 (AUTHZ-MATRIX-001 + EXPAND-001 패턴 재사용)
- 도메인별 @Nested 그룹 (예상 6~7 그룹)

### Step 2: Phase A 어휘 1~10 활성화 (Content + Auth + Dashboard)
- ROLE:CONTENT_ADMIN, CONTENT:READ, PAGE:READ/ROLLBACK/HISTORY:READ, SITE:WRITE, MENU:PERMISSION:WRITE, TEMPLATE:READ, USER:READ, SYSTEM:DASHBOARD
- ~10 endpoint × 3 시나리오 = 30 AC

### Step 3: Phase B 어휘 11~19 활성화 (System + Governance)
- SYSTEM:READ/SETTING:READ/WRITE/MAINT:READ/WRITE/LOG:READ/ADMIN, AUDIT:READ
- ~9 endpoint × 3 시나리오 = 27 AC + 분리 회귀 5~7건

### Step 4: ArchUnit baseline 갱신 + 회귀 검증 + Sync
- AuthorizationCoverageArchTest.baselineEndpoints() 35 → 50+ endpoint 갱신
- AUTHZ-MATRIX-001/EXPAND-001 회귀 0건 확인
- AUTHZ-AUTODETECT-001 4 AC 회귀 0건 확인
- README + CHANGELOG + Sync v0.2 Implemented

---

## 7. 후속 SPEC 안내

| SPEC | 목적 |
|------|------|
| **SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003** (가칭) | 운영 120 @PreAuthorize 전체 endpoint 적용 (35 → 120) |
| **SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-002** (가칭) | endpoint-vocabulary 1:1 매핑 검증 (현재는 어휘 set만 검증) |

---

## 8. 변경 이력

### Step 2 Phase A 시도 결과 (2026-05-11, 세션 후속)

본 SPEC RUN Step 2 Phase A 시도 (10 어휘 × 평균 3 시나리오 ≈ 30+ AC):

- **결과**: 34 tests / 13 FAILED — endpoint 매핑 가정 부정확
- **실패 패턴**:
  - "권한 부재 → 403" 기대 시나리오 다수가 401 또는 다른 응답 (operating policy 또는 endpoint path 차이)
  - "권한 보유 → 401/403 외" 기대 시나리오도 일부 실패 (404 또는 다른 endpoint mismatch)
  - 특히 PAGE:ROLLBACK + MENU:PERMISSION:WRITE 등 일부 어휘는 운영 컨트롤러 prerequisite (예: 데이터 존재 여부) 영향 가능성
- **revert 완료**: commit fc4a569 (Step 1 인프라 + smoke test) 상태로 복원

**다음 세션 RUN 진입 전 필수 작업**:
1. 각 운영 endpoint 정밀 검증 (실제 HTTP method + path + 권한 정책 정확 확인)
2. 운영 권한 정책에 OR 조건 추가 여부 확인 (SUPER_ADMIN 등 bypass)
3. 본 SPEC에 endpoint 매핑 표 정확화 (현재 표는 컨트롤러 파일명만 명시)
4. Step 2 시나리오 작성 전 endpoint 1개씩 단위 검증 → 확정 후 일괄 추가

### 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.2 | 2026-05-12 | MoAI orchestrator | Step 2 endpoint 정밀 진단 완료. Explore subagent로 14 컨트롤러 grep + Read 실측 → 19 어휘 × 24 endpoint 매핑 표 작성. Phase A 13 RED revert 원인 4가지 확정 (HTTP method 부정확, path variable 오류, USER:READ 단독 부재, 클래스 레벨 @PreAuthorize). SecurityConfig permitAll 화이트리스트 + authenticated() 정책 확인. Status: Planned → **Diagnosed** (Step 2 RUN 진입 준비 완료). 다음 작업: 24 endpoint 1개씩 단위 검증 후 일괄 시나리오 활성화. |
| v0.1 | 2026-05-11 | MoAI orchestrator | 초안 작성. AUTHZ-AUTODETECT-001 Step 2 GREEN 확정으로 ArchUnit 운영 실측 31 권한 어휘 노출 + AUTHZ-IT-EXPAND-001 12 어휘 커버 갭 19종 식별. REQ-AM-EXP2-001/002/003/004 정의. RUN Step 1~4 분해. 19 어휘 운영 endpoint 매핑 (CONTENT_ADMIN/CONTENT:READ/PAGE:READ/ROLLBACK/HISTORY:READ/SITE:WRITE/MENU:PERMISSION:WRITE/TEMPLATE:READ/USER:READ/SYSTEM:READ/DASHBOARD/SETTING:READ/WRITE/MAINT:READ/WRITE/LOG:READ/ADMIN/AUDIT:READ). 운영 코드 변경 0건 (IT 신설 전용). 본 SPEC 완성 시 OWASP A01 회귀 검출 능력 ArchUnit baseline 100% IT 커버 + 5중 검증 (HTTP 1차 19 + HTTP 확장 88 + HTTP 확장 2차 ~100 + 메소드 31 + ArchUnit 4) ≈ 240+ AC 달성. 사용자 결정 D1~D4 다음 세션 RUN 진입 전 확정 필요. |
