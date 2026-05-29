---
id: SPEC-CMS-DASHBOARD-PERSONALIZE-001
type: acceptance
created: 2026-05-29
updated: 2026-05-29
---

# Acceptance Criteria — 대시보드 개인화 (사용자별 위젯/스타일 저장)

각 시나리오는 Given-When-Then 형식을 따른다. AC ID 는 spec.md 의 REQ ID 와 1:N 매핑.

---

## AC-DP-001 위젯 가시성 토글 (REQ-DP-001)

### AC-DP-001-1 위젯 숨김 → 새로고침 → 여전히 숨김

**Given**
- 사용자 U1 이 layout L1 (자기 소유) 에 위젯 W1 (instance_id "w-pv-001") 포함하여 진입
- `user_dashboard_preference.hidden_widget_instance_ids = {}` (초기 상태)

**When**
- U1 이 W1 카드의 ⋯ 메뉴 > "숨기기" 클릭
- 브라우저 새로고침 (F5)

**Then**
- `hidden_widget_instance_ids = {"1": ["w-pv-001"]}` (L1.id = 1 가정)
- 새로고침 후에도 W1 이 화면에 표시되지 않음
- `dashboard_layout_widget` row 는 여전히 존재 (DB 직접 조회 검증)
- Network 탭에서 `GET /widgets/W1.id/data` 호출이 발생하지 않음

### AC-DP-001-2 숨김 위젯 복원

**Given**
- AC-DP-001-1 종료 상태 (W1 이 숨김)

**When**
- U1 이 "숨겨진 위젯 관리" 드로어 열기 → W1 의 "표시" 버튼 클릭

**Then**
- `hidden_widget_instance_ids = {"1": []}`
- W1 이 원래 `position` 좌표로 복원되어 표시됨
- `GET /widgets/W1.id/data` 호출 발생

### AC-DP-001-3 레이아웃 삭제 시 orphan 정리

**Given**
- U1 이 L1, L2 두 레이아웃 보유
- `hidden_widget_instance_ids = {"1": ["w-a"], "2": ["w-b"]}`

**When**
- U1 이 L1 (id=1) 삭제

**Then**
- `hidden_widget_instance_ids = {"2": ["w-b"]}` (L1 키 제거됨)

### AC-DP-001-4 다른 사용자 접근 차단

**Given**
- U1 의 preference 가 존재

**When**
- U2 가 `PATCH /preference/widgets/{L1}/hidden` 으로 U1 의 데이터 조작 시도

**Then**
- 403 Forbidden 응답
- U1 의 `hidden_widget_instance_ids` 변경되지 않음

### AC-DP-001-5 모든 위젯 표시

**Given**
- L1 에서 W1, W2, W3 모두 숨김 상태

**When**
- U1 이 편집 모드에서 "모든 위젯 표시" 클릭

**Then**
- `hidden_widget_instance_ids["1"] = []`
- 3개 위젯 모두 표시됨

---

## AC-DP-002 사용자별 시각 환경설정 (REQ-DP-002)

### AC-DP-002-1 다크 테마 적용

**Given**
- U1 의 `theme = LIGHT` (초기값)

**When**
- U1 이 환경설정 패널 열기 → 테마를 DARK 로 변경

**Then**
- 300ms 이내 `PATCH /preference` 호출 (body: `{theme: "DARK"}`)
- `<html data-theme="dark">` 클래스 적용
- 모든 차트가 다크 모드 팔레트로 재렌더
- axe-core 검사 위반 0건 (텍스트 4.5:1, 그래픽 3:1 명도 대비 충족)

### AC-DP-002-2 SYSTEM 테마는 OS 설정 추종

**Given**
- U1 의 `theme = SYSTEM`
- OS 다크 모드 활성

**When**
- 페이지 로드

**Then**
- `<html data-theme="dark">` 적용 (matchMedia 결과 반영)

**그리고 When**
- OS 를 라이트 모드로 변경

**Then**
- `<html data-theme="light">` 로 즉시 전환 (matchMedia 리스너 동작)

### AC-DP-002-3 밀도 + 폰트 배율 동시 적용

**Given**
- U1 의 `density = NORMAL, font_scale = 1.00`

**When**
- U1 이 `density = COMPACT, font_scale = 0.875` 로 변경

**Then**
- `<html>` 의 CSS 변수 `--density-padding`, `--font-base-size` 갱신
- ECharts 차트의 폰트 크기도 0.875 배율 적용 (`textStyle.fontSize` 동적 변경)
- 위젯 카드 패딩이 시각적으로 축소됨

### AC-DP-002-4 색약 팔레트 전역 우선

**Given**
- W1 의 `default_config.color_palette = "DEFAULT"`
- U1 의 `color_palette_preference = COLORBLIND`

**When**
- 대시보드 진입

**Then**
- W1 차트가 Bang Wong 8색 팔레트로 렌더링 (사용자 선호 우선)
- 색약 시뮬레이션 도구로 시리즈 식별 가능

### AC-DP-002-5 초기화 — hidden 은 보존

**Given**
- U1 의 환경설정: `theme=DARK, density=COMPACT, font_scale=0.875, color_palette_preference=COLORBLIND`
- `hidden_widget_instance_ids = {"1": ["w-a"]}`

**When**
- U1 이 "기본값으로 초기화" 클릭

**Then**
- 스타일 4종 모두 DEFAULT 로 복귀 (LIGHT/NORMAL/1.00/DEFAULT)
- `hidden_widget_instance_ids` 는 변경되지 않음 (`{"1": ["w-a"]}` 유지)

### AC-DP-002-6 PATCH 실패 시 롤백

**Given**
- U1 의 `theme = LIGHT`
- 서버가 일시적 500 응답

**When**
- U1 이 테마를 DARK 로 변경

**Then**
- UI 가 잠시 DARK 표시 후 LIGHT 로 복귀
- 에러 토스트 노출
- store 의 `theme` 값이 LIGHT 로 유지

---

## AC-DP-003 드래그앤드롭 재배치 (REQ-DP-003)

### AC-DP-003-1 편집 모드 진입 → 드래그 → 영속화

**Given**
- U1 이 L1 (자기 소유, 위젯 W1 at {x:0,y:0,w:6,h:4}) 진입
- 편집 모드 OFF

**When**
- U1 이 "편집 모드" 버튼 클릭
- W1 을 {x:6,y:0,w:6,h:4} 위치로 드래그

**Then**
- 드래그 종료 후 1초 뒤 `PATCH /layouts/1/positions` 호출
  - body: `[{instance_id: "w-W1", position: {x:6,y:0,w:6,h:4}}]`
- 200 OK
- DB 의 `dashboard_layout_widget.position` 갱신 확인

### AC-DP-003-2 위젯 겹침 → 거부 + 원위치

**Given**
- L1 에 W1 at {x:0,y:0,w:6,h:4}, W2 at {x:6,y:0,w:6,h:4}

**When**
- U1 이 W1 을 W2 위로 드래그 시도

**Then**
- 클라이언트 사전 검증으로 드래그가 W2 영역으로 진입하지 않음 (`preventCollision: true`)
- 또는 (백엔드 검증 우회 케이스) `PATCH /positions` 가 400 with `WidgetOverlapException` 응답
- W1 이 원래 좌표로 시각적 복귀
- 토스트 메시지 노출

### AC-DP-003-3 공유받은 레이아웃 — 편집 모드 금지

**Given**
- L_shared 가 U2 소유이고 U1 에게 공유됨 (`shared_with = ['EDITOR']`, U1 역할 = EDITOR)
- U1 이 L_shared 진입

**When**
- U1 이 "편집 모드" 버튼 확인

**Then**
- 버튼이 disabled 상태 (또는 보이지 않음)
- 직접 `PATCH /layouts/L_shared.id/positions` 호출 시 403

### AC-DP-003-4 모바일 — 편집 모드 비활성

**Given**
- 브라우저 width = 375px (모바일)
- U1 이 L1 진입

**When**
- 편집 모드 버튼 위치 확인

**Then**
- 버튼이 숨김 (display: none) — SPEC-CMS-008 §8.5 모바일 단일 컬럼 정책 준수

### AC-DP-003-5 낙관적 잠금 — 동시 편집 충돌

**Given**
- U1 이 탭 A 에서 L1 편집 중
- U1 이 탭 B 에서 같은 L1 편집 후 먼저 저장

**When**
- 탭 A 에서 드래그 후 저장 시도 (오래된 `updated_at` 보유)

**Then**
- 409 Conflict 응답
- 탭 A 에 "다른 탭에서 변경되었습니다. 새로고침해 주세요." 토스트
- 탭 A 의 변경사항은 폐기

### AC-DP-003-6 (Optional) 되돌리기

**Given**
- U1 이 W1 을 (0,0) → (6,0) 으로 드래그하여 저장 (5초 전)

**When**
- U1 이 "되돌리기" 버튼 클릭 (1분 이내)

**Then**
- W1 이 (0,0) 으로 복귀
- `PATCH /positions` 가 (0,0) 으로 추가 호출
- "되돌리기" 버튼은 다시 비활성

---

## AC-DP-API API 계약 검증

### AC-DP-API-1 GET /preference — Lazy 생성

**Given**
- U1 의 preference row 가 DB 에 존재하지 않음

**When**
- `GET /api/v1/dashboard/preference` 호출

**Then**
- 200 OK
- 모든 컬럼이 DEFAULT 값으로 응답
- DB 에 row 1개 INSERT 됨 (idempotent)

### AC-DP-API-2 PATCH /preference — 부분 업데이트

**Given**
- U1 의 preference 가 `{theme: LIGHT, density: NORMAL}`

**When**
- `PATCH /preference` body `{theme: "DARK"}`

**Then**
- 200 OK
- `theme = DARK, density = NORMAL` (density 변경되지 않음)
- `updated_at` 갱신

### AC-DP-API-3 PATCH /preference — 잘못된 enum 거부

**Given**
- 정상 인증

**When**
- `PATCH /preference` body `{theme: "PINK"}`

**Then**
- 400 Bad Request with validation error

---

## Quality Gate (TRUST 5 + 본 SPEC 고유)

### Tested

- [ ] 단위 테스트 커버리지 ≥ 85% (Service, Mapper, Composable)
- [ ] 통합 테스트: 5개 신규 엔드포인트 + 1개 수정 엔드포인트 모두 커버
- [ ] E2E 테스트 (Playwright): AC-DP-001/002/003 의 핵심 5 시나리오 GREEN
- [ ] V39 마이그레이션 rollback 테스트 (Flyway repair → undo 시뮬레이션)

### Readable

- [ ] 한국어 i18n 키 25개 모두 등록 + 누락된 ko 번역 0
- [ ] PreferencePanel.vue 코드 ≤ 300 LOC
- [ ] 모든 신규 public API 에 javadoc/JSDoc 주석

### Unified

- [ ] 기존 컨벤션 (`kr.co.ircp.cms.domain.dashboard.*` 패키지) 준수
- [ ] Pinia store 명명 규칙 `dashboardXxxStore` 준수
- [ ] ESLint / Checkstyle 위반 0건

### Secured

- [ ] 모든 5+1 엔드포인트가 본인 데이터만 접근 (SpEL `#userId == authentication.principal.id`)
- [ ] JSONB 직접 SQL injection 불가 (PreparedStatement 만 사용)
- [ ] `font_scale` CHECK 제약으로 임의 값 거부
- [ ] CORS / CSRF 기존 정책 준수 (변경 없음)

### Trackable

- [ ] 모든 commit 이 `SPEC-CMS-DASHBOARD-PERSONALIZE-001` 참조
- [ ] V39 마이그레이션이 `flyway_schema_history` 에 기록
- [ ] `@MX:ANCHOR` 추가: `DashboardPreferenceController` 의 5개 메서드 (fan_in ≥ 3 예상)
- [ ] `@MX:NOTE` 추가: `hidden_widget_instance_ids` JSONB 구조 설명

---

## Definition of Done

본 SPEC 은 다음 모두 충족 시 "Tested" 상태로 전이:

1. **모든 AC-DP-* 시나리오 GREEN** (위 21개)
2. **마이그레이션 V39 본 환경/스테이징 환경 모두 적용 완료**
3. **SPEC-CMS-008 의 기존 IT 테스트가 단 1건도 실패하지 않음** (회귀 없음)
4. **KWCAG 2.2 AA 자동 검사 (axe-core) 위반 0건** — 특히 DARK 테마 신규 검증
5. **모바일/태블릿/데스크톱 반응형 회귀** 통과
6. **`grid-layout-plus` 라이선스(MIT)** 가 프로젝트 라이선스 화이트리스트 통과
7. **CHANGELOG.md** 에 v1.8.0 (예정) 항목 추가
8. **이 SPEC 의 `status: Tested` 로 갱신** + HISTORY 에 v0.x → v1.0 기록

---

## Out-of-Scope (검증하지 않는 것)

- SPEC-CMS-008 이 이미 검증한 위젯 정의/렌더링/캐시/내보내기 동작 (회귀만 확인, 신규 검증 안 함)
- 다중 모니터 / 듀얼 모드 / 위젯 그룹화 (spec.md §4.2 비범위)
- 환경설정 export/import 파일 포맷
- 협업 (다른 사용자가 동시 편집 표시)
