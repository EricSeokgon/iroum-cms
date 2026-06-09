---
id: SPEC-CMS-ADMIN-TEST-001
version: 0.3.0
status: Completed
created: 2026-05-14
updated: 2026-05-14
author: ircp
priority: P1
parent: SPEC-CMS-001 v0.5
---

# SPEC-CMS-ADMIN-TEST-001 — Admin SPA 테스트 인프라 복구 및 커버리지 확장

## HISTORY

- 2026-05-14 (v0.2.0): Admin SPA 테스트 272/272 전체 통과 확인. jsdom 환경 `el-form.validate()` 제약 해결($refs 목 패턴 적용), ForgotPasswordView OTP 셀렉터 수정, RoleFormView Promise 패턴 전환. 상태 Draft → Tested 갱신.
- 2026-05-14 (v0.1.0): 초안 작성. Vitest 셋업 부재로 인한 52개 실패 테스트 진단 및 30+ 미테스트 View 식별. 3단계 복구 계획 수립.

---

## 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-ADMIN-TEST-001 |
| 제목 | Admin SPA 테스트 인프라 복구 및 커버리지 확장 |
| 우선순위 | P1 |
| 상위 SPEC | SPEC-CMS-001 v0.5 (Umbrella) |
| 작업 영역 | `frontend/admin/` (Vue 3 + Element Plus + Vitest) |
| 현재 상태 | 40 test files / 212 tests → 18 files (52 tests) 실패, 22 files (160 tests) 통과 |
| 목표 상태 | 신규 ≥25 test files / 누적 통과 ≥260개 / Element Plus jsdom 정상 마운트 |
| 진행 단계 | Draft → Planned → In Progress → Tested → Implemented |

---

## 배경 및 목적

### 배경

`frontend/admin/`는 SPEC-CMS-001 1차 출시 완료(commit `1a5076d`) 이후 200+ 화면을 보유한 Vue 3 + TypeScript + Element Plus 기반 관리자 SPA로 성장하였다. 그러나 테스트 인프라는 다음과 같은 핵심 결함을 안고 있다.

1. **Vitest 셋업 파일 부재**: `tests/setup.ts`가 존재하지 않고, `vite.config.ts`의 `test` 섹션에 `setupFiles` 등록도 누락되어 있다.
2. **Element Plus 글로벌 등록 누락**: 테스트 환경에서 `ElementPlus.install()`이 호출되지 않아 `el-input`, `el-form`, `el-form-item` 등 컴포넌트의 DOM 구조가 정상 마운트되지 않는다. 그 결과 `setValue`, `el-form-item.is-error` 등의 어설션이 모두 실패한다.
3. **jsdom 환경 호환성 결함**: `ResizeObserver`, `window.matchMedia` 등 Element Plus 내부 의존 브라우저 API가 jsdom에 없어 컴포넌트 마운트 시점에 예외가 발생한다.
4. **i18n / Pinia 부재**: 테스트마다 `createI18n` / `createTestingPinia` 부트스트랩 보일러플레이트가 반복되거나 누락된다.

### 미테스트 View 현황

`src/views/` 디렉토리에는 76개 Vue View 파일이 있으나, 테스트는 40개(컴포넌트·스토어·라우터 포함)에 그친다. 30+ 핵심 View가 단위 테스트 없이 운영 중이다. 식별된 주요 미테스트 View (운영 영향도 순):

- **대시보드/홈**: DashboardView, HomeView, NotFoundView
- **정책 도메인**: PolicyListView, PolicyDetailView, PolicyMatchView, PolicyDispatchView, PolicySubscriptionView, MatchResultView
- **콘텐츠/게시판**: BoardFormView, FaqListView, QnaListView, QnaDetailView, PageListView
- **발간/배포**: PublicationListView, PublicationDetailView
- **안전/사고**: IncidentListView, IncidentDetailView
- **모니터링/감사**: AccessLogView, AuditLogView, BatchLogsView, GovernanceStatsView, GuidelineReportView, QualityReportView
- **기타**: DataDictionaryView, MediaCollectionView, I18nEditorView, NotificationSettingsView, OrganizationHistoryDialog

### 목적

1. **Element Plus + jsdom 호환 Vitest 셋업 도입**으로 현재의 잘못된 실패를 제거하고 회귀를 방지한다.
2. **실패 테스트 복구**를 통해 통과율을 75%(160/212) → 95%+(200/212+) 수준으로 끌어올린다.
3. **운영 핵심 View 13종에 대한 신규 테스트(≥25 파일, ≥100 케이스)**를 추가하여 1차 출시 이후의 회귀를 조기 감지한다.
4. **테스트 작성 패턴 표준화**(헬퍼/팩토리/모킹 규약)로 후속 SPEC의 테스트 작성 비용을 낮춘다.

---

## 범위

### In Scope

- `frontend/admin/tests/setup.ts` 신설 및 `vite.config.ts` 의 `test.setupFiles` 등록
- Element Plus 글로벌 등록 (`ElementPlus`, `ElLoadingDirective`, `ElInfiniteScroll`, 아이콘 컴포넌트)
- jsdom 누락 API 폴리필 (`ResizeObserver`, `window.matchMedia`, `IntersectionObserver`)
- `vue-i18n` 테스트용 인스턴스 부트스트랩 (`ko`, `en` 메시지 스텁)
- Pinia 테스트 유틸 (`createTestingPinia`) 표준 헬퍼
- `axios` mock 리셋 훅 (`beforeEach`/`afterEach`)
- 기존 18개 실패 테스트 파일 복구 (셀렉터/어설션/모킹 보정 포함)
- 우선순위 미테스트 View 13종에 대한 신규 `*.spec.ts` 작성 (마운트, 데이터 렌더, 빈/에러 상태 최소 커버)
- 공용 테스트 헬퍼 도입 (`tests/helpers/mountView.ts`, `tests/helpers/mockApi.ts`)

### Out of Scope

- E2E 테스트 (Playwright/Cypress) — 별도 SPEC 대상
- Visual regression 테스트 (Chromatic/Percy)
- Storybook 도입 및 스토리 작성
- Backend (Spring) 테스트 — 다른 SPEC 트랙
- 라우터/스토어의 대규모 리팩터링 — 테스트가 의존하는 최소한의 변경만 허용
- 디자인 토큰/스타일 시스템 변경
- 의존 라이브러리 메이저 업그레이드 (Vue, Element Plus, Vitest 등은 현 버전 고정)

### 제외 항목 (Exclusions)

- Element Plus 자체의 내부 컴포넌트에 대한 테스트는 작성하지 않는다. 라이브러리 신뢰는 전제로 둔다.
- 100% 라인 커버리지를 목표로 하지 않는다. 핵심 마운트/조회/에러 경로 우선이며, 분기 커버리지는 후속 SPEC.
- 신규 View 테스트는 i18n 키 누락 검증을 강제하지 않는다 (별도 i18n 검증 SPEC 트랙 존재).
- 본 SPEC은 코드 구현부(`src/views/`)의 행위 변경을 수반하지 않는다. View가 테스트 가능한 형태가 아닐 경우, 최소 attribute(`data-testid`) 추가만 허용한다.

---

## 기술 스택 및 제약사항

### 기술 스택

| 영역 | 스택 | 버전(고정) |
|------|------|-----------|
| Framework | Vue | 3.5+ |
| Language | TypeScript | 5.x |
| Build | Vite | 5.x |
| State | Pinia | 2.x |
| Router | Vue Router | 4.x |
| UI Kit | Element Plus | 2.8+ |
| i18n | vue-i18n | 9.x |
| Test Runner | Vitest | 2.1.8 |
| Test Utils | @vue/test-utils | 2.x |
| DOM | jsdom | (Vitest 기본) |
| HTTP Mock | vitest mock + axios | — |

### 제약사항

- **모노레포 경계**: 본 SPEC의 모든 변경은 `frontend/admin/` 하위로 한정한다.
- **상위 SPEC**: SPEC-CMS-001 v0.5(Umbrella)의 1차 출시 완료 상태를 회귀시켜서는 안 된다.
- **기존 통과 테스트 보존**: 셋업 도입 후에도 현재 통과 중인 160개 테스트는 모두 통과해야 한다.
- **언어 규약**: 코드/테스트 코멘트는 한국어 허용(`code_comments: ko`), 식별자는 영문.
- **CI 호환**: `npx vitest run`(headless)에서 단일 명령으로 통과해야 한다.
- **테스트 격리**: 테스트 간 Pinia/axios 상태가 누설되지 않아야 한다.

---

## 요구사항 (EARS)

### Group A — 테스트 인프라 (T-001)

- **REQ-A-01 (Event-driven)**: When 개발자가 `frontend/admin/` 디렉토리에서 `npx vitest run` 명령을 실행하면, the system shall `tests/setup.ts`를 자동으로 로드하여 Element Plus, vue-i18n, Pinia를 모든 테스트에 적용해야 한다.
- **REQ-A-02 (Ubiquitous)**: The system shall jsdom 환경에서 `ResizeObserver`, `window.matchMedia`, `IntersectionObserver`를 폴리필하여 Element Plus 컴포넌트가 정상 마운트되도록 한다.
- **REQ-A-03 (Ubiquitous)**: The system shall 모든 테스트 시작 전에 `axios` 모킹 상태를 초기화하고, 종료 후에는 핸들러를 해제해야 한다.
- **REQ-A-04 (State-driven)**: While 셋업 파일이 적용된 상태에서, the system shall 현재 통과 중인 160개 테스트 케이스가 모두 통과 상태를 유지해야 한다.
- **REQ-A-05 (Unwanted)**: The system shall not 셋업 파일에서 운영 코드(`src/`)를 임포트하거나 글로벌 부수효과를 발생시켜서는 안 된다. 셋업은 테스트 한정 헬퍼와 폴리필만 포함해야 한다.

### Group B — 실패 테스트 복구 (T-002)

- **REQ-B-01 (Event-driven)**: When 셋업 도입 후 기존 실패 테스트를 재실행하면, the system shall 52개 실패 테스트 중 최소 80%(≥42개) 이상이 통과해야 한다.
- **REQ-B-02 (State-driven)**: While `el-input` 셀렉터로 직접 `setValue`를 호출하던 테스트가 존재하는 동안, the system shall `find('input')`/`find('textarea')` 기반의 정정된 셀렉터로 동일 어설션이 통과하도록 보정한다.
- **REQ-B-03 (Event-driven)**: When `el-form-item.is-error` 검증을 수행하면, the system shall Element Plus 유효성 라이프사이클(`form.validate()` 비동기 완료)을 await한 뒤 단언해야 한다.
- **REQ-B-04 (Ubiquitous)**: The system shall PermissionMatrixGrid, PasswordChangeView, LoginHistoryView 세 가지 핵심 실패 스펙을 100% 통과 상태로 복구해야 한다.
- **REQ-B-05 (State-driven)**: While 셋업/셀렉터 보정 후에도 실패하는 테스트가 존재하는 동안, the system shall 해당 테스트를 (a) UI 가정 오류 (b) 구현 변경에 따른 회귀 (c) 명세-구현 불일치 중 하나로 분류한 진단 결과를 작성해야 한다.

### Group C — 신규 커버리지 (T-003 ~ T-005)

- **REQ-C-01 (Ubiquitous)**: The system shall DashboardView, NotFoundView 각각에 대해 마운트 / KPI(있을 경우) 렌더링 / 에러·빈 상태 테스트 케이스를 작성해야 한다.
- **REQ-C-02 (Ubiquitous)**: The system shall PolicyListView, PolicyDetailView, PolicyMatchView 각각에 대해 마운트 / API 모킹 기반 데이터 렌더 / 빈·에러 상태 테스트 케이스를 작성해야 한다.
- **REQ-C-03 (Ubiquitous)**: The system shall BoardFormView, FaqListView, QnaListView 각각에 대해 마운트 / 필수 폼 필드 또는 목록 렌더 / 빈 상태 테스트 케이스를 작성해야 한다.
- **REQ-C-04 (Ubiquitous)**: The system shall IncidentListView, IncidentDetailView, PublicationListView, PublicationDetailView 각각에 대해 마운트 / 데이터 렌더 / 에러 상태 테스트 케이스를 작성해야 한다.
- **REQ-C-05 (State-driven)**: While 본 SPEC이 완료되는 시점에, the system shall 신규 테스트 파일 ≥25개, 신규 테스트 케이스 ≥100개, 전체 통과 테스트 누계 ≥260개를 만족해야 한다.
- **REQ-C-06 (Optional)**: Where 공용 헬퍼(`mountView`, `mockApi`)가 도입되어 있으면, the system shall 신규 테스트에서 동일 헬퍼를 사용해 보일러플레이트를 50% 이상 줄여야 한다.

---

## 구현 계획

### Phase Table

| Phase | Task ID | 제목 | 산출물 | 의존성 | 우선순위 |
|-------|---------|------|--------|--------|---------|
| 1 | T-001 | Vitest 셋업 인프라 구축 | `tests/setup.ts`, `vite.config.ts` 업데이트, `tests/helpers/*` | — | High |
| 2 | T-002 | 18개 실패 테스트 파일 복구 | 보정된 spec 파일들 + 분류 진단 메모 | T-001 | High |
| 3 | T-003 | 대시보드/정책 View 테스트 신규 작성 | DashboardView, NotFoundView, Policy* 3종 spec | T-001 | High |
| 3 | T-004 | 게시판/콘텐츠 View 테스트 신규 작성 | BoardFormView, FaqListView, QnaListView, QnaDetailView spec | T-001 | Medium |
| 3 | T-005 | 안전/발간 View 테스트 신규 작성 | Incident*, Publication* 4종 spec | T-001 | Medium |

### 기술 접근

**T-001 셋업 구성요소**:
1. `tests/setup.ts`에서 `ElementPlus.install`, `ElLoadingDirective`, `ElInfiniteScroll`를 글로벌 등록
2. Element Plus 아이콘은 필요한 것만 명시적으로 글로벌 컴포넌트로 등록(전체 등록은 토큰 낭비)
3. `ResizeObserver` 더블, `window.matchMedia` 더블 글로벌 주입
4. `createI18n({ locale: 'ko', messages: { ko: {}, en: {} } })` 인스턴스 노출
5. `tests/helpers/mountView.ts`에서 `(component, options) => mount(component, { global: { plugins: [...] } })` 표준 마운트 래퍼 제공
6. `tests/helpers/mockApi.ts`에서 axios 응답 팩토리 및 reset 헬퍼 제공
7. `vite.config.ts` 의 `test.setupFiles: ['./tests/setup.ts']` 등록 및 `test.environment: 'jsdom'` 확인

**T-002 복구 전략**:
1. 셋업 적용 후 `npx vitest run` 재실행하여 현황 재집계
2. 잔여 실패 테스트를 다음 카테고리로 분류:
   - (a) 셀렉터 패턴 정정으로 즉시 통과 가능
   - (b) Element Plus 비동기 라이프사이클 await 누락
   - (c) 구현 변경에 따른 정당한 회귀 → 테스트 어설션 갱신
   - (d) 진짜 회귀 버그 → 테스트는 유지하고 별도 이슈 등록(본 SPEC 범위 외)
3. (a)(b)(c) 일괄 정정, (d) 분류 결과를 본 SPEC 진행 로그에 기록

**T-003~T-005 신규 테스트 패턴**:
- 각 View 당 최소 3 케이스: ① 마운트 성공 ② 데이터 렌더 ③ 빈 또는 에러 상태
- 라우터 파라미터 의존 View(Detail*)는 `vue-router`의 `setup-paths`를 모킹
- API 의존 View는 `mockApi` 헬퍼로 응답 스텁 주입
- 데이터 표/카드 렌더링은 `data-testid` 우선 셀렉터로 결합도 최소화

### 리스크

| 리스크 | 영향 | 완화 |
|--------|------|------|
| Element Plus 글로벌 등록 시점 부수효과 | 일부 컴포넌트가 stub되어 렌더 누락 | `global.stubs` 사용 금지, 실 컴포넌트 렌더 검증 |
| jsdom 폴리필 불완전 | Resize 관련 코드 경로 실패 | `ResizeObserver` 더블에 `observe/unobserve/disconnect` 모두 구현 |
| 신규 테스트 작성 중 View 구현 의존 발견 | 테스트가 깨지기 쉬움 | `data-testid` 추가는 허용, 로직 변경은 금지 |
| axios 모킹 누설 | 다른 테스트가 영향 | `beforeEach`에서 `vi.clearAllMocks()` + 핸들러 리셋 강제 |
| 통과율 80% 미달 | T-002 인수 기준 미충족 | (d) 카테고리 분리로 진짜 회귀 버그는 본 SPEC 범위 외로 명시 |

### 검증 전략

- 각 Phase 종료 시 `npx vitest run` 전수 실행 결과 첨부
- T-001 종료 시 통과 ≥160, T-002 종료 시 통과 ≥200, T-005 종료 시 통과 ≥260 누계 확인
- 신규 테스트 파일은 모두 `tests/views/**/*.spec.ts` 경로 규칙 준수
- 잔여 (d) 카테고리 회귀 후보는 별도 보고서 `.moai/reports/` 하위로 분리

---

## 인수 기준 (Acceptance Criteria)

### Group A — 테스트 인프라

**A-01: 셋업 도입 후 기존 통과 테스트 회귀 없음**

- Given `frontend/admin/` 디렉토리에서 현재 22개 파일 / 160개 테스트가 통과하는 상태에서
- When `tests/setup.ts`를 신설하고 `vite.config.ts`의 `test.setupFiles`에 등록한 후 `npx vitest run`을 실행하면
- Then 기존에 통과하던 160개 테스트가 모두 통과 상태를 유지해야 한다 (회귀 0건).

**A-02: 셋업 적용 후 실패 테스트 80% 이상 회복**

- Given 현재 18개 파일 / 52개 테스트가 Element Plus 마운트 실패로 실패하는 상태에서
- When `tests/setup.ts` 적용 후 동일 테스트들을 재실행하면
- Then 최소 42개(80%) 이상의 테스트가 통과 상태로 전환되어야 한다.

**A-03: Element Plus 컴포넌트가 jsdom에서 정상 마운트**

- Given `el-input`, `el-select`, `el-form`, `el-table`을 사용하는 임의의 View를 마운트하는 테스트에서
- When 셋업 파일이 `ResizeObserver`와 `window.matchMedia`를 폴리필한 상태로 마운트를 시도하면
- Then 콘솔 에러 없이 마운트가 성공하고, 내부 DOM(`input`, `.el-form-item__content` 등)이 실제로 렌더되어야 한다.

### Group B — 실패 테스트 복구

**B-01: PermissionMatrixGrid.spec.ts 전체 통과**

- Given `tests/components/PermissionMatrixGrid.spec.ts` 가 현재 실패 상태인 환경에서
- When T-001 셋업 적용 후 필요한 셀렉터/await 보정을 수행한 뒤 해당 스펙을 실행하면
- Then 해당 파일 내 모든 케이스가 통과해야 한다.

**B-02: PasswordChangeView.spec.ts setValue 오류 해결**

- Given 현재 `Cannot call setValue on an empty DOMWrapper` 에러로 실패하는 `PasswordChangeView.spec.ts` 에서
- When 셋업 적용 + `el-input` 래퍼를 통한 내부 `input`/`textarea` 셀렉터로 보정하면
- Then 비밀번호 입력/유효성/제출 케이스가 모두 통과해야 한다.

**B-03: LoginHistoryView.spec.ts 전체 통과**

- Given `tests/views/LoginHistoryView.spec.ts` 가 현재 실패 상태인 환경에서
- When 셋업 + axios 모킹 표준화 + 셀렉터 보정을 적용하면
- Then 로그인 이력 조회/필터/페이지네이션 케이스가 모두 통과해야 한다.

**B-04: 누적 통과 테스트 ≥ 200**

- Given T-002 완료 시점에
- When `npx vitest run` 전수 실행 결과를 집계하면
- Then 전체 통과 테스트 케이스 수가 200개 이상이어야 한다.

### Group C — 신규 커버리지

**C-01: DashboardView 테스트 신규 추가**

- Given `tests/views/DashboardView.spec.ts`가 존재하지 않는 상태에서
- When 본 SPEC의 T-003을 수행하면
- Then `DashboardView.spec.ts`가 추가되고, 최소 3개 케이스(마운트, KPI 또는 위젯 렌더, 로딩 또는 에러 상태)가 모두 통과해야 한다.

**C-02: 정책 관련 View 3종 테스트 신규 추가**

- Given PolicyListView/PolicyDetailView/PolicyMatchView에 대한 spec이 없는 상태에서
- When T-003을 수행하면
- Then 세 View 각각의 spec 파일이 추가되고, 각 파일은 최소 3개 케이스(마운트, 모킹 기반 데이터 렌더, 빈 또는 에러 상태)를 포함하여 모두 통과해야 한다.

**C-03: 게시판/콘텐츠 View 3종 테스트 신규 추가**

- Given BoardFormView/FaqListView/QnaListView에 대한 spec이 없는 상태에서
- When T-004를 수행하면
- Then 세 View 각각의 spec 파일이 추가되고, 폼·목록 핵심 경로가 모두 통과해야 한다.

**C-04: 안전/발간 View 4종 테스트 신규 추가**

- Given IncidentListView/IncidentDetailView/PublicationListView/PublicationDetailView에 대한 spec이 없는 상태에서
- When T-005를 수행하면
- Then 네 View 각각의 spec 파일이 추가되고, 목록·상세 경로에서 마운트/렌더/에러 케이스가 모두 통과해야 한다.

**C-05: 신규 파일 ≥25, 신규 케이스 ≥100, 누적 통과 ≥260**

- Given T-005 완료 시점에
- When 변경된 파일 목록과 `npx vitest run` 결과를 집계하면
- Then (a) 신규 추가된 `*.spec.ts` 파일이 25개 이상, (b) 신규 테스트 케이스가 100개 이상, (c) 전체 통과 테스트 누계가 260개 이상이어야 한다.

### Definition of Done

- [ ] `frontend/admin/tests/setup.ts` 생성 및 `vite.config.ts` 등록 완료
- [ ] `tests/helpers/mountView.ts`, `tests/helpers/mockApi.ts` 생성 완료
- [ ] 기존 통과 160개 테스트 회귀 0건
- [ ] 기존 실패 52개 중 ≥42개(80%) 통과 전환
- [ ] PermissionMatrixGrid / PasswordChangeView / LoginHistoryView 3종 100% 통과
- [ ] 신규 spec 파일 ≥25, 신규 케이스 ≥100, 누적 통과 ≥260
- [ ] 잔여 실패 테스트의 (a)/(b)/(c)/(d) 카테고리 분류 진단 결과 기록
- [ ] `npx vitest run` 단일 명령으로 헤드리스 환경에서 통과
- [ ] 본 SPEC `status: Draft → Tested` 전이 및 HISTORY 갱신

---

## 참고

- 상위 SPEC: SPEC-CMS-001 v0.5 (Umbrella, 1차 출시 완료 — commit `1a5076d`)
- 관련 기존 테스트 디렉토리: `frontend/admin/tests/`
- 본 SPEC은 행위 변경이 없는 테스트 인프라/커버리지 SPEC이므로 LSP 영향도는 0이며, 운영 코드 변경은 `data-testid` 추가에 한정한다.
