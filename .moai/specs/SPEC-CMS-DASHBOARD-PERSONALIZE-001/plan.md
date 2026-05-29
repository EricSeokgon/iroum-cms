---
id: SPEC-CMS-DASHBOARD-PERSONALIZE-001
type: plan
created: 2026-05-29
updated: 2026-05-29
---

# Plan — 대시보드 개인화 (사용자별 위젯/스타일 저장)

## 1. 구현 접근 방식 (High-Level)

본 SPEC 은 SPEC-CMS-008 의 인프라를 **재사용**하고 **누락된 사용자 경험 계층** 만 추가한다. 따라서 구현은 3개 독립 트랙으로 분리 가능하지만, 같은 DB 테이블(`user_dashboard_preference`)과 같은 프론트엔드 store 를 공유하므로 단일 SPEC 으로 묶는다.

```
[Track A: DB + 백엔드 환경설정 API]
    ↓ (preference 모델 확정)
[Track B: 프론트엔드 환경설정 store + 적용 레이어]
    ↓ (테마/밀도/팔레트 동작)
[Track C: 위젯 가시성 토글 UI]
    ↓ (편집 가능 상태 확보)
[Track D: 드래그앤드롭 재배치 UI + PATCH positions API]
```

## 2. 마일스톤 (우선순위 기반, 시간 추정 없음)

### Milestone M1 — DB 마이그레이션 + 백엔드 모델 (Priority High)

목표: `user_dashboard_preference` 테이블을 도입하고 기본 CRUD API 제공.

작업:
- `backend/src/main/resources/db/migration/V39__user_dashboard_preference.sql` 신규 작성
- `kr.co.ircp.cms.domain.dashboard.entity.UserDashboardPreference` 엔티티
- `UserDashboardPreferenceMapper` (MyBatis) + 매핑 XML
- `UserDashboardPreferenceService` (`getOrCreate(userId)`, `patch(userId, dto)`, `reset(userId)`, `toggleWidgetVisibility(userId, layoutId, instanceId, hidden)`)
- `DashboardPreferenceController` (`GET/PATCH/POST` 엔드포인트)
- DTO: `PreferenceResponse`, `PreferencePatchRequest`, `WidgetVisibilityToggleRequest`

완료 기준:
- 마이그레이션이 통합 테스트 환경에서 idempotent 하게 실행됨
- 5개 신규 엔드포인트 모두 200/4xx 정상 응답
- 본인 외 사용자 데이터 접근 시 403 확인
- 단위/통합 테스트 GREEN (커버리지 ≥ 85%)

### Milestone M2 — 백엔드 위치 PATCH API (Priority High)

목표: `PATCH /api/v1/dashboard/layouts/{id}/positions` 추가하여 DnD 결과 영속화 지원.

작업:
- `DashboardLayoutController` 에 `updatePositions` 메서드 추가
- `DashboardLayoutService.updatePositions(userId, layoutId, List<PositionUpdate>)`
- 위젯 겹침 검증 로직 (server-side, REQ-DP-003-3)
- owner_id 검증 (REQ-DP-003-4 공유받은 레이아웃 차단)
- Optimistic locking (`dashboard_layout.updated_at` 비교)

완료 기준:
- 겹침 발생 시 400 with `WidgetOverlapException`
- 비소유자 PATCH 시 403
- 통합 테스트로 동시 편집 시나리오 검증 (낙관적 잠금)

### Milestone M3 — 프론트엔드 환경설정 store + 적용 레이어 (Priority High)

목표: Pinia store 로 preference 를 중앙 관리하고 전역 CSS/팔레트에 반영.

작업:
- `frontend/admin/src/stores/dashboardPreference.ts` 신규 (Pinia)
  - state: `preference: PreferenceState`
  - actions: `fetch()`, `patch(partial)`, `reset()`, `toggleWidget(layoutId, instanceId)`
  - getters: `effectivePalette`, `effectiveTheme`
- `frontend/admin/src/composables/useThemeApplier.ts` — `<html>` 클래스/CSS 변수 토글
- `frontend/admin/src/composables/useChartPaletteOverride.ts` — ECharts 옵션 빌더에 주입
- `frontend/admin/src/api/dashboardPreference.ts` — axios wrapper 5개
- 다크 모드 색 토큰: `frontend/admin/src/styles/tokens-dark.scss`
- App.vue 에서 앱 부팅 시 `dashboardPreferenceStore.fetch()` 호출

완료 기준:
- 테마 변경 시 새로고침 없이 즉시 반영
- 차트 팔레트 사용자 선호 우선 동작 확인
- 컴포넌트 단위 테스트 (Vitest) GREEN

### Milestone M4 — 위젯 가시성 토글 UI (Priority Medium)

목표: 위젯 카드에 숨기기 메뉴 + "숨겨진 위젯 관리" 드로어 추가.

작업:
- `DashboardMainView.vue` 위젯 카드 헤더에 ⋯ 메뉴(`el-dropdown`) 추가 → "숨기기" 액션
- `frontend/admin/src/components/dashboard/HiddenWidgetsDrawer.vue` 신규 — 숨김 목록 + 복원 버튼
- 위젯 데이터 페치 시 `hidden_widget_instance_ids` 필터링 (REQ-DP-001-3)
- "모든 위젯 표시" 버튼 (편집 모드 헤더)

완료 기준:
- 숨김 → 새로고침 → 여전히 숨김 (영속)
- 숨김 위젯은 `/widgets/{id}/data` API 호출이 발생하지 않음 (Network 탭 확인)
- 레이아웃 삭제 시 hidden 항목도 정리 (Backend M1 의 trigger 또는 service 로직)

### Milestone M5 — 드래그앤드롭 재배치 UI (Priority Medium)

목표: `grid-layout-plus` 도입하여 편집 모드에서 드래그·리사이즈 가능.

작업:
- `frontend/admin/package.json` 에 `grid-layout-plus` 의존성 추가 + 라이선스 검증(MIT)
- `DashboardMainView.vue` 의 정적 grid 를 `<grid-layout>` + `<grid-item>` 으로 교체
- 편집 모드 토글 버튼 (헤더) + `isDraggable` / `isResizable` 바인딩
- `layout-updated` 이벤트 → debounce 1초 → `PATCH /layouts/{id}/positions`
- 충돌 사전 검증 (`preventCollision: true`)
- 모바일(< 768px) 에서 편집 모드 비활성 (REQ-DP-003 + SPEC-CMS-008 §8.5)
- Optional: 되돌리기 버튼 (브라우저 메모리 스냅샷, REQ-DP-003-5)

완료 기준:
- 60fps 드래그 (성능 측정)
- PATCH 실패 시 토스트 + 원위치 복귀
- 공유받은 레이아웃에서 편집 모드 버튼 disabled

### Milestone M6 — 환경설정 패널 UI (Priority Medium)

목표: 사용자가 환경설정을 한 곳에서 변경할 수 있는 패널.

작업:
- `frontend/admin/src/components/dashboard/PreferencePanel.vue` (el-drawer)
  - 테마 라디오 (LIGHT/DARK/SYSTEM)
  - 밀도 라디오 (COMPACT/NORMAL/COMFORTABLE)
  - 폰트 배율 슬라이더 (0.875/1.0/1.125)
  - 팔레트 라디오 (DEFAULT/COLORBLIND/MONOCHROME) + 미리보기 swatch
  - "기본값으로 초기화" 버튼
- AdminLayout.vue 헤더에 환경설정 아이콘 추가
- i18n 키 약 25개 (`ko.json` + `en.json`)

완료 기준:
- 모든 변경이 debounce 300ms 후 PATCH
- 실패 시 토스트 + 이전 값 롤백
- 초기화 시 hidden 제외 모든 컬럼 DEFAULT 복귀

### Milestone M7 — 통합 검증 + 접근성 (Priority High)

목표: 전 기능 통합 시나리오 검증 + KWCAG 2.2 AA 재검증.

작업:
- E2E (Playwright): 환경설정 변경 → 새로고침 → 복원 → 위젯 숨김 → DnD 재배치 → 초기화
- axe-core 자동 검사 (DARK 테마 추가 검증)
- 색약 시뮬레이션 도구로 COLORBLIND 팔레트 검증
- 다중 탭 동시 편집 시나리오 (낙관적 잠금 동작 확인)
- 모바일/태블릿/데스크톱 반응형 회귀

완료 기준:
- E2E 시나리오 5종 GREEN
- axe-core 위반 0건
- LCP < 2초 유지 (preference fetch 가 LCP 에 영향 없음 확인)

## 3. 기술적 접근

### 3.1 백엔드

- **Spring Boot 3.5.9 + MyBatis** (SPEC-CMS-008 스택 유지)
- **`@AuthenticationPrincipal`** 로 본인 user_id 추출, SpEL `#userId == authentication.principal.id` 검증
- **JSONB 갱신**: PostgreSQL `jsonb_set` 사용하여 `hidden_widget_instance_ids` 부분 업데이트
- **Optimistic Lock**: `dashboard_layout.updated_at` 비교 (Compare-And-Swap 패턴)

### 3.2 프론트엔드

- **Vue 3.5 + Pinia + Element Plus** (기존 스택 유지)
- **grid-layout-plus** v1.x (vue-grid-layout 의 Vue 3 fork, MIT)
- **CSS 변수 기반 테마**: `:root[data-theme="dark"] { --bg: #1a1a1a; }`
- **ECharts 팔레트 주입**: 차트 옵션 빌더 시 store getter 참조
- **Debounce**: lodash `debounce` 또는 `@vueuse/core` `useDebounceFn`

### 3.3 마이그레이션 정책

- 단일 V39 파일로 통합 (DB 변경 1회)
- 모든 컬럼 DEFAULT → 백필 SQL 불요
- Lazy creation: `GET /preference` 시 row 없으면 INSERT … ON CONFLICT DO NOTHING

## 4. 위험 완화

| 위험 (spec.md §10) | 완화 작업 위치 |
|---|---|
| RISK-DP-01 동시 편집 충돌 | M2 — Optimistic locking 구현 |
| RISK-DP-02 hidden 비대화 | M1 — 사용자당 layout 100개 cap (애플리케이션 단) |
| RISK-DP-03 DARK 명도 대비 | M3 — tokens-dark.scss 사전 정의 + M7 axe-core 검증 |
| RISK-DP-04 font_scale 텍스트 잘림 | M5 — 위젯 카드 overflow 정책 변경 |
| RISK-DP-05 schema 변경 | M1 — schema_version 컬럼 + service lazy migration 훅 |
| RISK-DP-06 grid-layout-plus 호환 | M5 — Spike 작업: 30분 PoC 후 결정 |

## 5. 의존성

### 외부 의존성

- 백엔드: 없음 (기존 의존성 재사용)
- 프론트엔드: `grid-layout-plus@^1.0.0` 신규 (MIT)

### SPEC 의존성

- **선행 SPEC**: SPEC-CMS-008 v0.5 (Tested 상태, 인프라 완성됨) ← 반드시 GREEN 유지
- **충돌 없음**: 본 SPEC 은 SPEC-CMS-008 의 어떤 컬럼/API 도 변경하지 않음 (additive only)

## 6. 추정 영향 범위

| 영역 | 신규 | 수정 | 삭제 |
|---|---|---|---|
| DB 마이그레이션 | 1 (V39) | 0 | 0 |
| 백엔드 Java | 약 8 파일 (entity/dto/mapper/service/controller) | 1 (DashboardLayoutController) | 0 |
| 프론트엔드 Vue/TS | 약 6 파일 (store/composable/component/api) | 2 (DashboardMainView.vue, AdminLayout.vue) | 0 |
| 테스트 | 약 6 파일 (단위 + 통합 + E2E) | 0 | 0 |
| i18n | 25 키 | 0 | 0 |

## 7. Run Phase 권장 모드

- **개발 방법론**: TDD (project 의 quality.yaml 기본값) — Controller/Service/Component 모두 테스트 우선
- **에이전트 추천**:
  - `expert-backend` (Spring Boot + MyBatis + JSONB)
  - `expert-frontend` (Vue 3 + Pinia + grid-layout-plus + 다크 모드)
  - `expert-testing` (Playwright E2E + axe-core)
- **Worktree 권장**: 단일 worktree (3개 트랙이지만 공유 store/테이블 강결합)
