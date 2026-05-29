---
id: SPEC-CMS-DASHBOARD-PERSONALIZE-001
version: 0.1.0
status: Tested
created: 2026-05-29
updated: 2026-05-29
author: manager-spec
priority: P1
parent: SPEC-CMS-008 v0.5
related:
  - SPEC-CMS-008 (시각화 대시보드 + KPI 통합 — 인프라 재사용 대상)
  - SPEC-CMS-002 (RBAC — 사용자 식별/권한)
issue_number: TBD
---

# SPEC-CMS-DASHBOARD-PERSONALIZE-001 대시보드 개인화 (사용자별 위젯/스타일 저장)

## HISTORY

- v0.1 / 2026-05-29 / manager-spec / 신규 작성. SPEC-CMS-008 의 `dashboard_layout` / `saved_view` 인프라 위에 1) **사용자별 위젯 가시성 토글**(레이아웃을 훼손하지 않고 숨김), 2) **사용자별 스타일 환경설정**(테마/밀도/팔레트/폰트 배율), 3) **드래그앤드롭 위젯 재배치 UI** 3가지 누락 기능을 정의. DB 마이그레이션 V39 단일 파일에 `user_dashboard_preference` 테이블 신규 도입.

---

## 1. 개요

본 SPEC 은 SPEC-CMS-008 v0.5 가 이미 구축한 대시보드 인프라(`dashboard_widget` / `dashboard_layout` / `dashboard_layout_widget` / `saved_view`) 위에 **개인화 누락 격차** 를 채우는 자식 SPEC 이다.

핵심 가치:

- 사용자가 **레이아웃을 깨뜨리지 않고** 특정 위젯을 일시 숨김/표시 토글 가능
- 사용자가 **자신만의 시각적 환경설정**(다크/라이트 테마, 밀도, 폰트 배율, 색약 팔레트) 을 저장하여 모든 대시보드에 일관 적용
- 사용자가 **드래그앤드롭으로 위젯 위치를 재배치** 하고 그 결과가 즉시 영속화

본 SPEC 은 새로운 위젯 타입·차트·KPI 를 도입하지 않으며, **사용자 경험 계층의 누락분만** 채운다.

---

## 2. SPEC-CMS-008 이 이미 제공하는 것 (재사용)

| 기능 | 위치 | 비고 |
|---|---|---|
| 사용자별 대시보드 레이아웃 CRUD | `dashboard_layout` 테이블 + `/api/v1/dashboard/layouts` | REQ-VIZ-002-D-2 |
| 위젯 12-grid 위치 저장 | `dashboard_layout_widget.position {x,y,w,h}` | REQ-VIZ-002-D-1 |
| 다중 레이아웃 + 기본 레이아웃 | `dashboard_layout.is_default` (사용자당 1) | REQ-VIZ-002-D-4 |
| 사용자별 필터 저장 | `saved_view` 테이블 + `/api/v1/dashboard/views` | REQ-VIZ-004-D-3 |
| 필터 URL 동기화 | Vue Router query string | REQ-VIZ-004-D-2 |
| 색약 팔레트 옵트인 | 차트별 `default_config.color_palette` | §8.4 |
| KWCAG 2.2 AA + 반응형 | DashboardMainView.vue 전반 | §12.1 |

→ 본 SPEC 은 위 목록을 **변경하거나 대체하지 않는다.**

---

## 3. 본 SPEC 이 신규 도입하는 것 (격차)

| 격차 | 현재 상태 | 본 SPEC 해결 방식 |
|---|---|---|
| 위젯 일시 숨김 | 위젯을 숨기려면 레이아웃에서 삭제해야 함 → 다시 보려면 재배치 필요 | `user_dashboard_preference.hidden_widget_instance_ids` 에 toggle |
| 사용자별 시각 테마 | 시스템 단일 테마 + Element Plus 기본 | 사용자별 `theme` (LIGHT/DARK/SYSTEM) 저장 |
| 사용자별 밀도/폰트 배율 | 고정 | `density` (COMPACT/NORMAL/COMFORTABLE) + `font_scale` (0.875/1.0/1.125) |
| 사용자별 팔레트 선호 | 위젯별 설정만 가능, 전역 미지원 | `color_palette_preference` (DEFAULT/COLORBLIND/MONOCHROME) 전역 오버라이드 |
| 드래그앤드롭 재배치 UI | 위치는 DB 에 있으나 UI 가 정적 그리드 | `vue-grid-layout` 도입 + position PATCH API |

---

## 4. 범위 및 비범위

### 4.1 범위 (포함)

- 사용자별 위젯 가시성 토글 (instance_id 단위, 레이아웃 비파괴)
- 사용자별 시각 환경설정 5종 저장/복원 (테마/밀도/폰트배율/팔레트/사이드바 접힘 상태)
- 드래그앤드롭 위젯 재배치 UI + 자동 저장 (debounce 1초)
- 환경설정 초기화 ("시스템 기본값으로 되돌리기")
- 환경설정의 다중 세션 동기화 (다른 탭에서 변경 시 새로고침 안내)

### 4.2 비범위 (제외)

| 비범위 | 결정 | 향후 검토 |
|---|---|---|
| 위젯 타입 신규 추가 | SPEC-CMS-008 v0.5 의 9 종으로 충분 | 별도 SPEC |
| 위젯 크기 (w,h) 사용자별 오버라이드 | DnD 재배치 시 position 전체 갱신으로 흡수 | — |
| 다중 모니터 / 듀얼 레이아웃 | 1차 출시 불필요 | v0.2+ |
| 환경설정 export/import | 사용 빈도 낮음 | 옵션 |
| 환경설정 부서별 강제 (정책) | SUPER_ADMIN 이 사용자 자율 침해 | 정책 변경 없음 |
| 위젯 그룹화/탭 분리 | UX 복잡도 증가 | v0.3 |
| 새로운 차트 라이브러리 | ECharts 5 유지 | 없음 |

---

## 5. 데이터 모델

### 5.1 신규 테이블 — `user_dashboard_preference`

사용자 1명당 1행. PK 가 `user_id` (1:1 관계). 위젯 가시성·전역 스타일·UI 상태를 단일 row 에 통합.

```sql
-- V39__user_dashboard_preference.sql
CREATE TABLE user_dashboard_preference (
    user_id                          BIGINT       PRIMARY KEY
                                     REFERENCES users(id) ON DELETE CASCADE,

    -- 5.1.1 위젯 가시성 (레이아웃 비파괴 숨김)
    -- 형식: {"{layout_id}": ["{instance_id_1}", "{instance_id_2}"]}
    -- 예: {"12": ["w-pv-001", "w-policy-cvr-003"]}
    hidden_widget_instance_ids       JSONB        NOT NULL DEFAULT '{}'::jsonb,

    -- 5.1.2 시각 테마
    theme                            VARCHAR(16)  NOT NULL DEFAULT 'SYSTEM'
        CHECK (theme IN ('LIGHT','DARK','SYSTEM')),

    -- 5.1.3 밀도 / 폰트 배율
    density                          VARCHAR(16)  NOT NULL DEFAULT 'NORMAL'
        CHECK (density IN ('COMPACT','NORMAL','COMFORTABLE')),
    font_scale                       NUMERIC(3,2) NOT NULL DEFAULT 1.00
        CHECK (font_scale IN (0.875, 1.00, 1.125)),

    -- 5.1.4 색상 팔레트 전역 선호 (위젯별 설정보다 우선)
    color_palette_preference         VARCHAR(16)  NOT NULL DEFAULT 'DEFAULT'
        CHECK (color_palette_preference IN ('DEFAULT','COLORBLIND','MONOCHROME')),

    -- 5.1.5 UI 상태 (사이드바, 펼침/접힘)
    sidebar_collapsed                BOOLEAN      NOT NULL DEFAULT FALSE,

    -- 5.1.6 메타
    schema_version                   SMALLINT     NOT NULL DEFAULT 1,
    created_at                       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_pref_updated ON user_dashboard_preference(updated_at DESC);

COMMENT ON TABLE user_dashboard_preference IS
  'SPEC-CMS-DASHBOARD-PERSONALIZE-001: 사용자별 대시보드 개인화 환경설정 (1:1)';
COMMENT ON COLUMN user_dashboard_preference.hidden_widget_instance_ids IS
  'layout_id 별 숨김 위젯 instance_id 목록. 삭제가 아닌 비파괴 토글.';
COMMENT ON COLUMN user_dashboard_preference.color_palette_preference IS
  'dashboard_widget.default_config.color_palette 보다 우선 적용되는 사용자 전역 팔레트';
```

### 5.2 기존 테이블 변경 — `dashboard_layout_widget` (position PATCH 지원)

기존 컬럼 변경 없음. **단지 본 SPEC 의 REQ-DP-003 가 PATCH API 를 통해 `position` 컬럼을 갱신할 뿐**. DDL 변경 없음.

### 5.3 마이그레이션

- 신규 파일: `V39__user_dashboard_preference.sql` (단일 마이그레이션)
- 기존 사용자에 대해서는 lazy 생성 (최초 GET 호출 시 INSERT … ON CONFLICT DO NOTHING)
- 백필 불필요 (모든 컬럼이 DEFAULT 보유)

---

## 6. 요구사항 (EARS)

### REQ-DP-001 위젯 가시성 토글

- **REQ-DP-001-1 (위젯 숨김 — Event-driven)**
  WHEN 사용자가 위젯 카드의 "숨기기" 버튼을 클릭하면, THEN 시스템은 `user_dashboard_preference.hidden_widget_instance_ids[layout_id]` 배열에 해당 `instance_id` 를 추가하고, 프런트엔드는 즉시 해당 위젯을 DOM 에서 제거해야 한다. 이때 `dashboard_layout_widget` row 는 보존되어야 한다.
- **REQ-DP-001-2 (숨김 위젯 복원 — Event-driven)**
  WHEN 사용자가 "숨겨진 위젯 관리" 패널에서 위젯을 다시 표시하면, THEN 시스템은 해당 `instance_id` 를 배열에서 제거하고 원래 `position` 으로 위젯을 복원해야 한다.
- **REQ-DP-001-3 (가시성 적용 시점 — State-driven)**
  IF 사용자가 대시보드를 로드할 때 `hidden_widget_instance_ids[layout_id]` 에 포함된 위젯이 있으면, THEN 해당 위젯의 데이터 페치(`GET /widgets/{id}/data`) 를 **수행하지 않아야** 한다. (불필요한 API 호출/캐시 부하 방지)
- **REQ-DP-001-4 (레이아웃 삭제 시 정리 — Event-driven)**
  WHEN `dashboard_layout` 이 삭제되면, THEN 트리거 또는 애플리케이션 로직으로 `hidden_widget_instance_ids` 에서 해당 `layout_id` 키를 제거해야 한다. (orphan 정리)
- **REQ-DP-001-5 (전체 표시 복원 — Event-driven)**
  WHEN 사용자가 "모든 위젯 표시" 를 클릭하면, THEN 시스템은 현재 `layout_id` 의 hidden 배열을 빈 배열로 초기화해야 한다.

### REQ-DP-002 사용자별 시각 환경설정

- **REQ-DP-002-1 (테마 적용 — Ubiquitous)**
  시스템은 사용자가 선택한 `theme` (LIGHT/DARK/SYSTEM) 을 모든 대시보드 화면에 적용해야 하며, `SYSTEM` 인 경우 브라우저 `prefers-color-scheme` 미디어 쿼리를 따라야 한다.
- **REQ-DP-002-2 (밀도/폰트 배율 적용 — Ubiquitous)**
  프런트엔드는 `density` 와 `font_scale` 을 CSS 변수(`--density-padding`, `--font-base-size`) 로 변환하여 `<html>` 루트 요소에 적용해야 한다. ECharts 차트의 폰트 크기도 동일 배율로 스케일되어야 한다.
- **REQ-DP-002-3 (팔레트 전역 우선 — State-driven)**
  IF `color_palette_preference` 가 `DEFAULT` 가 아니면, THEN 모든 차트 렌더링 시 사용자 선호 팔레트가 `dashboard_widget.default_config.color_palette` 보다 우선 적용되어야 한다.
- **REQ-DP-002-4 (환경설정 영속화 — Event-driven)**
  WHEN 사용자가 환경설정 패널에서 값을 변경하면, THEN 시스템은 즉시 (debounce 300ms) `PATCH /api/v1/dashboard/preference` 로 영속화해야 하며, 실패 시 토스트로 알리고 이전 값으로 롤백해야 한다.
- **REQ-DP-002-5 (초기화 — Event-driven)**
  WHEN 사용자가 "기본값으로 초기화" 를 클릭하면, THEN 시스템은 모든 컬럼을 DEFAULT 값으로 재설정해야 한다 (단, `hidden_widget_instance_ids` 는 초기화 대상에서 제외 — 사용자 의도와 분리).

### REQ-DP-003 드래그앤드롭 재배치

- **REQ-DP-003-1 (DnD 활성화 — State-driven)**
  IF 사용자가 "편집 모드" 를 활성화하면, THEN 모든 위젯이 `vue-grid-layout` 기반의 draggable + resizable 가능 상태로 전환되어야 한다. 편집 모드 OFF 시 정적 그리드로 복귀한다.
- **REQ-DP-003-2 (위치 자동 저장 — Event-driven)**
  WHEN 사용자가 위젯을 드래그하여 위치를 변경하면, THEN 시스템은 debounce 1초 후 `PATCH /api/v1/dashboard/layouts/{id}/positions` 로 변경된 `instance_id` 들의 새 `{x,y,w,h}` 를 일괄 전송해야 한다.
- **REQ-DP-003-3 (충돌 거부 — Unwanted)**
  시스템은 드래그 결과가 위젯 간 겹침을 발생시키면 저장을 거부하고 UI 에서 원위치 복귀시켜야 한다. (서버 검증 + 클라이언트 사전 검증 이중)
- **REQ-DP-003-4 (편집 모드 권한 — State-driven)**
  IF 사용자가 공유받은 (자기 소유가 아닌) 레이아웃을 보고 있으면, THEN 편집 모드 진입을 금지해야 한다 (REQ-VIZ-002-D-3 읽기전용 규칙 보존).
- **REQ-DP-003-5 (실행 취소 — Optional)**
  WHERE 사용자가 마지막 PATCH 직후 1분 이내에 "되돌리기" 를 클릭하면, THEN 시스템은 직전 position 스냅샷으로 복원해야 한다. (브라우저 세션 메모리 보관, 영속화 불요)

---

## 7. API 명세

base path: `/api/v1/dashboard`

| Method | Path | 설명 | 권한 | REQ |
|---|---|---|---|---|
| GET | `/preference` | 본인 환경설정 조회 (없으면 DEFAULT 생성·반환) | 인증된 사용자 | REQ-DP-002-1~3 |
| PATCH | `/preference` | 환경설정 부분 갱신 (변경된 필드만 body) | 본인 | REQ-DP-002-4 |
| POST | `/preference/reset` | DEFAULT 로 초기화 (hidden 제외) | 본인 | REQ-DP-002-5 |
| PATCH | `/preference/widgets/{layout_id}/hidden` | 위젯 가시성 토글 (body: `{instance_id, hidden:bool}`) | 본인 | REQ-DP-001-1,2,5 |
| PATCH | `/layouts/{id}/positions` | 위젯 위치 일괄 갱신 (body: `[{instance_id, position:{x,y,w,h}}, ...]`) | 본인 (owner) | REQ-DP-003-2,3,4 |

응답 스키마 예시 (`GET /preference`):

```json
{
  "user_id": 42,
  "hidden_widget_instance_ids": {
    "12": ["w-pv-001"]
  },
  "theme": "DARK",
  "density": "COMPACT",
  "font_scale": 0.875,
  "color_palette_preference": "COLORBLIND",
  "sidebar_collapsed": true,
  "schema_version": 1,
  "updated_at": "2026-05-29T10:00:00Z"
}
```

---

## 8. 비기능 요구사항

| 항목 | 임계값 | 측정 |
|---|---|---|
| `GET /preference` p95 | < 50ms | PK 단건 조회 |
| `PATCH /preference` p95 | < 100ms | 단행 UPDATE |
| `PATCH /layouts/{id}/positions` p95 (위젯 ≤ 20) | < 200ms | 트랜잭션 일괄 UPDATE |
| DnD 시각적 지연 (드래그 → 렌더) | < 16ms (60fps) | vue-grid-layout 권장값 |
| 환경설정 변경 → 모든 차트 재렌더 | < 500ms | ECharts setOption |
| 다크 모드 명도 대비 (KWCAG) | 텍스트 4.5:1, 그래픽 3:1 | axe-core CI |
| 색약 팔레트 식별성 | Bang Wong 8색, 적록/청황 색약 모두 식별 | 수동 검증 |

---

## 9. 권한 매트릭스

| 역할 | 본인 preference | 본인 hidden_widgets | 본인 소유 layout DnD | 공유받은 layout DnD |
|---|---|---|---|---|
| SUPER_ADMIN | C/R/U | C/R/U | 가능 | 불가 (정책 우선) |
| DEPT_ADMIN | C/R/U | C/R/U | 가능 | 불가 |
| EDITOR | C/R/U | C/R/U | 가능 | 불가 |
| VIEWER | C/R/U | C/R/U | 가능 | 불가 |

→ 본 SPEC 의 모든 기능은 **본인 데이터에 한정**. 타인 preference 조회/수정 API 없음.

---

## 10. 위험 및 대응

| ID | 위험 | 영향 | 대응 |
|---|---|---|---|
| RISK-DP-01 | 드래그 중 다른 탭에서 동시 편집 → position 충돌 | 데이터 불일치 | `updated_at` optimistic locking, 충돌 시 사용자에게 새로고침 안내 |
| RISK-DP-02 | hidden_widget_instance_ids 가 비대해짐 (수천 레이아웃 보유) | JSONB 검색 성능 저하 | 사용자당 max 100 layout 제한 (SPEC-CMS-008 정책과 일관) |
| RISK-DP-03 | DARK 테마 차트 색상 대비 부족 | KWCAG 위반 | 다크 모드 전용 팔레트 토큰 (`.moai/design/tokens/palette-dark.json`) 사전 정의 |
| RISK-DP-04 | font_scale 1.125 적용 시 위젯 텍스트 잘림 | UX 저하 | 위젯 카드 `overflow: hidden` → `overflow: auto` + 최소 높이 조정 |
| RISK-DP-05 | 환경설정 schema 변경 시 기존 row 마이그레이션 | 깨진 환경설정 | `schema_version` 컬럼 + 애플리케이션 측 lazy migration |
| RISK-DP-06 | DnD 라이브러리 (vue-grid-layout) 가 Vue 3.5 와 충돌 | 빌드 실패 | `grid-layout-plus` (Vue 3 fork) 사용 검토. research.md 에서 결정 |

---

## 11. 외부 의존성

- **신규 프론트엔드 라이브러리**: `grid-layout-plus` ^1.0.0 (vue-grid-layout 의 Vue 3 fork, MIT 라이선스)
- **백엔드 신규 의존성**: 없음 (기존 MyBatis + PostgreSQL JSONB 재사용)
- **i18n 키 신규**: 약 25개 (preference 패널 라벨 + DnD 안내 메시지)

---

## 12. Exclusions (What NOT to Build)

본 SPEC 이 의도적으로 다루지 않는 항목:

- **위젯 타입 추가/변경**: SPEC-CMS-008 의 9 종 위젯 그대로 사용. 새 차트 타입은 별도 SPEC.
- **위젯별 사용자 오버라이드 (예: 사용자 A 만의 위젯 색상)**: 전역 팔레트 선호로 일괄 대응. per-widget per-user 오버라이드는 비범위.
- **부서/조직 단위 강제 환경설정**: 사용자 자율 침해. 정책 변경 없음.
- **환경설정 export/import (.json 다운로드)**: 사용 빈도 낮음. 옵션 차후.
- **다중 모니터 / 듀얼 모드 레이아웃**: 1차 출시 범위 외.
- **위젯 그룹화·탭 분리·접힘**: UX 복잡도 증가. v0.3 검토.
- **드래그 중 실시간 다른 사용자 표시 (협업)**: 사용자별 개인화 SPEC 의 범위 아님.
- **모바일 DnD**: 모바일에서는 편집 모드 비활성 + 정적 그리드 (SPEC-CMS-008 §8.5 모바일 단일 컬럼 정책 유지).
- **환경설정 변경 이력 audit**: PII 무관 + 사용자 자율 영역으로 audit 대상 아님.

---

## 13. 참조 문서

| 문서 | 절 | 핵심 |
|---|---|---|
| SPEC-CMS-008 v0.5 | §4 데이터 모델 | dashboard_layout / saved_view 재사용 |
| SPEC-CMS-008 v0.5 | §11 권한 매트릭스 | 본인 데이터 한정 원칙 계승 |
| SPEC-CMS-008 v0.5 | §12.1 KWCAG | 본 SPEC 의 DARK 테마도 동일 기준 적용 |
| SPEC-CMS-002 | §8 권한 매트릭스 | 사용자 식별 (`@AuthenticationPrincipal`) |
| 프로젝트 `.moai/project/tech.md` | Vue 3.5 + Element Plus | grid-layout-plus 호환 검증 필요 |
| backend `db/migration/V17__dashboard_schema.sql` | 기존 대시보드 DDL | V39 추가 위치 결정 근거 |

---

## 14. 검증 체크리스트

- [ ] V39 마이그레이션이 단일 파일이며 V38 다음 번호
- [ ] `user_dashboard_preference` PK 가 `user_id` (1:1)
- [ ] 모든 컬럼이 DEFAULT 값을 가져 백필 불요
- [ ] EARS 5 패턴 사용 (Ubiquitous/Event-driven/State-driven/Unwanted/Optional)
- [ ] 본 SPEC 의 모든 API 가 본인 데이터에만 접근
- [ ] SPEC-CMS-008 의 어떤 컬럼/API 도 변경하지 않음 (additive only)
- [ ] Exclusions 절에 최소 5개 항목 명시 (현재 9개)
- [ ] grid-layout-plus 라이선스 검증 (MIT)
