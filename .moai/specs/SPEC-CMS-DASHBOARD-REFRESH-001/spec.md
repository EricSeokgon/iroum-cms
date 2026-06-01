---
id: SPEC-CMS-DASHBOARD-REFRESH-001
version: 0.1.0
status: Draft
created: 2026-06-01
updated: 2026-06-01
author: manager-spec
priority: P2
parent: SPEC-CMS-DASHBOARD-PERSONALIZE-001 v0.1
related:
  - SPEC-CMS-DASHBOARD-PERSONALIZE-001 (대시보드 개인화 — 인프라 재사용 대상)
  - SPEC-CMS-008 (시각화 대시보드 + KPI — 위젯 데이터 페치 출처)
  - SPEC-CMS-002 (RBAC — 사용자 식별)
issue_number: TBD
---

# SPEC-CMS-DASHBOARD-REFRESH-001 사용자별 대시보드 자동 새로고침 주기 개인화

## HISTORY

- v0.1 / 2026-06-01 / manager-spec / 신규 작성. SPEC-CMS-DASHBOARD-PERSONALIZE-001 이 구축한 `user_dashboard_preference` 1:1 환경설정 인프라 위에 **사용자별 자동 새로고침 주기** 단일 기능을 추가. DB 는 단일 마이그레이션 `V42` 로 `refresh_interval_seconds INT DEFAULT NULL` 컬럼만 추가(NULL = OFF). 백엔드는 기존 PATCH `/preference` 부분 갱신 경로 재사용, 프런트엔드는 기존 store/패널/API 모듈 확장. 새 위젯·새 차트·새 데이터 출처 도입 없음.

---

## 1. 개요

### 1.1 목적

사용자가 대시보드의 **자동 새로고침 주기를 본인 취향대로 선택**(OFF / 30초 / 1분 / 5분 / 15분 / 30분)하고, 그 주기에 맞춰 위젯 데이터가 **전체 페이지 리로드 없이** 자동 갱신되도록 한다.

### 1.2 배경

현재 대시보드에는 자동 새로고침 기능이 전혀 없다. 사용자는 최신 KPI/위젯 데이터를 보려면 수동으로 브라우저 새로고침(F5) 해야 하며 이때 전체 페이지가 리로드되어 스크롤·편집 상태가 초기화된다. SPEC-CMS-DASHBOARD-PERSONALIZE-001 이 이미 사용자별 1:1 환경설정 테이블(`user_dashboard_preference`)과 GET/PATCH 환경설정 API, Pinia store, 환경설정 패널을 갖추고 있으므로, 본 SPEC 은 그 위에 **단일 컬럼 + 단일 UI 컨트롤 + 클라이언트 타이머** 만 얹는 최소 확장이다.

### 1.3 범위

- 사용자별 새로고침 주기 1종 저장(`refresh_interval_seconds`, NULL=OFF)
- 활성 주기에 따른 위젯 데이터 자동 재페치(부분 갱신, 전체 리로드 금지)
- 다음 새로고침까지 남은 시간 카운트다운 인디케이터
- 기존 DashboardPreferencePanel 에서 주기 선택
- 브라우저 탭 비활성 시 일시정지(Page Visibility API)

본 SPEC 은 새 위젯 타입·차트·KPI·데이터 출처를 **도입하지 않으며**, 갱신은 SPEC-CMS-008 의 기존 위젯 데이터 페치 경로(`GET /widgets/{id}/data`)를 재사용한다.

---

## 2. SPEC-CMS-DASHBOARD-PERSONALIZE-001 이 이미 제공하는 것 (재사용)

| 자산 | 위치 (실측) | 재사용 방식 |
|---|---|---|
| 1:1 사용자 환경설정 테이블 | `user_dashboard_preference` (V39) | `refresh_interval_seconds` 컬럼만 추가 |
| 환경설정 조회 (lazy 생성) | `GET /api/v1/dashboard/preference` | 응답 DTO 에 신규 필드 추가 |
| 환경설정 부분 갱신 | `PATCH /api/v1/dashboard/preference` | 동일 엔드포인트에 신규 필드 1개 수용 |
| 엔티티 | `UserDashboardPreference` (`.../preference/entity`) | `Integer refreshIntervalSeconds` 필드 추가 |
| 서비스 | `UserDashboardPreferenceService(Impl)` | `update()` 부분 갱신 로직에 신규 필드 흡수 |
| MyBatis 매퍼 | `UserDashboardPreferenceMapper` + XML | UPDATE/SELECT 컬럼 목록에 추가 |
| Pinia store | `frontend/admin/src/stores/dashboardPreferenceStore.ts` | `refreshIntervalSeconds` 상태/액션 추가 |
| API 모듈 | `frontend/admin/src/api/dashboardPreference.ts` | 신규 필드 PATCH 페이로드 포함 |
| 환경설정 패널 | `frontend/admin/src/views/dashboard/DashboardPreferencePanel.vue` | 주기 선택 컨트롤 1개 추가 |
| 위젯 데이터 페치 | SPEC-CMS-008 `GET /widgets/{id}/data` | 타이머가 호출, 신규 API 없음 |

→ 본 SPEC 은 위 자산을 **변경 대체하지 않고 additive 확장만** 한다.

---

## 3. 본 SPEC 이 신규 도입하는 것 (격차)

| 격차 | 현재 상태 | 본 SPEC 해결 방식 |
|---|---|---|
| 자동 새로고침 자체 | 없음 (수동 F5) | `refresh_interval_seconds` 기반 클라이언트 타이머 |
| 주기 영속화 | 없음 | `user_dashboard_preference.refresh_interval_seconds` (V42) |
| 부분 갱신 (전체 리로드 회피) | 없음 | 위젯별 `GET /widgets/{id}/data` 재호출 후 setOption |
| 남은 시간 가시화 | 없음 | 카운트다운 인디케이터 |
| 탭 비활성 시 자원 절약 | 없음 | Page Visibility API 로 타이머 일시정지 |

---

## 4. 데이터 모델

### 4.1 기존 테이블 변경 — `user_dashboard_preference`

신규 컬럼 1개 추가. 기존 컬럼·제약 변경 없음. 모든 기존 row 는 DEFAULT NULL(=OFF) 로 백필 불요.

```sql
-- V42__user_dashboard_preference_refresh_interval.sql
ALTER TABLE user_dashboard_preference
    ADD COLUMN refresh_interval_seconds INT DEFAULT NULL
        CHECK (refresh_interval_seconds IN (30, 60, 300, 900, 1800));

COMMENT ON COLUMN user_dashboard_preference.refresh_interval_seconds IS
  'SPEC-CMS-DASHBOARD-REFRESH-001: 대시보드 자동 새로고침 주기(초). NULL = OFF. 허용값 30/60/300/900/1800.';
```

- NULL = OFF (자동 새로고침 비활성), 그 외는 30s/1m/5m/15m/30m 에 해당하는 초 단위 값
- CHECK 제약으로 임의 값 차단 (UI 옵션과 1:1 대응)
- `schema_version` 컬럼은 기존 1 유지 (컬럼 추가는 nullable + DEFAULT 라 lazy migration 불요)

### 4.2 마이그레이션

- 신규 파일: `V42__user_dashboard_preference_refresh_interval.sql` (단일 마이그레이션, V41 다음 번호)
- 백필 불필요 (DEFAULT NULL 이 곧 OFF 의미)
- 기존 `defaults(userId)` 팩토리는 `refreshIntervalSeconds = null` 로 유지 (명시적 설정 불요, 빌더 미지정 시 자동 null)

---

## 5. 요구사항 (EARS)

### REQ-REFRESH-001 새로고침 주기 설정 및 영속화

- **REQ-REFRESH-001-1 (주기 선택 — Event-driven)**
  WHEN 사용자가 환경설정 패널에서 새로고침 주기(OFF/30s/1m/5m/15m/30m)를 선택하면, THEN 시스템은 선택값을 `refresh_interval_seconds`(OFF=NULL)로 `PATCH /api/v1/dashboard/preference` 를 통해 영속화해야 한다.
- **REQ-REFRESH-001-2 (허용값 검증 — Unwanted)**
  시스템은 `refresh_interval_seconds` 가 허용 집합(NULL, 30, 60, 300, 900, 1800)에 속하지 않으면 저장을 거부하고 400 을 반환해야 한다(서버 검증 + DB CHECK 이중).
- **REQ-REFRESH-001-3 (주기 복원 — State-driven)**
  IF 사용자가 대시보드를 로드할 때 `refresh_interval_seconds` 가 NULL 이 아니면, THEN 프런트엔드는 저장된 주기로 자동 새로고침 타이머를 시작해야 한다. NULL 이면 타이머를 시작하지 않아야 한다.

### REQ-REFRESH-002 위젯 데이터 자동 갱신 (전체 리로드 금지)

- **REQ-REFRESH-002-1 (주기 도달 시 부분 갱신 — Event-driven)**
  WHEN 새로고침 타이머가 설정된 주기에 도달하면, THEN 시스템은 현재 표시 중인 각 위젯의 데이터(`GET /widgets/{id}/data`)만 재페치하여 차트를 갱신해야 하며, 전체 페이지 리로드(`location.reload`)를 수행해서는 안 된다.
- **REQ-REFRESH-002-2 (숨김 위젯 제외 — State-driven)**
  IF 위젯이 `hidden_widget_instance_ids`(SPEC-CMS-DASHBOARD-PERSONALIZE-001)에 포함되어 있으면, THEN 자동 갱신은 해당 위젯의 데이터를 재페치하지 않아야 한다(불필요한 API 호출 방지).
- **REQ-REFRESH-002-3 (갱신 실패 격리 — Unwanted)**
  시스템은 일부 위젯의 자동 갱신이 실패하더라도 다른 위젯의 갱신·표시를 중단시켜서는 안 되며, 실패한 위젯은 직전 데이터를 유지하고 비차단 알림(인라인 상태)만 표시해야 한다.

### REQ-REFRESH-003 카운트다운 인디케이터

- **REQ-REFRESH-003-1 (남은 시간 표시 — Ubiquitous)**
  자동 새로고침이 활성일 때, 시스템은 다음 새로고침까지 남은 시간을 나타내는 카운트다운 인디케이터를 대시보드에 표시해야 한다.
- **REQ-REFRESH-003-2 (수동 즉시 새로고침 — Optional)**
  WHERE 사용자가 카운트다운 인디케이터의 "지금 새로고침" 액션을 클릭하면, THEN 시스템은 즉시 위젯 데이터를 갱신하고 카운트다운을 주기값으로 재설정해야 한다.

### REQ-REFRESH-004 탭 비활성 시 일시정지

- **REQ-REFRESH-004-1 (비가시 탭 일시정지 — State-driven)**
  IF 브라우저 탭이 비가시 상태(`document.visibilityState === 'hidden'`)가 되면, THEN 시스템은 자동 새로고침 타이머를 일시정지하여 백그라운드 API 호출을 발생시키지 않아야 한다.
- **REQ-REFRESH-004-2 (가시 복귀 시 재개 — Event-driven)**
  WHEN 탭이 다시 가시 상태(`visible`)로 전환되면, THEN 시스템은 타이머를 재개해야 하며, 비가시 동안 주기가 1회 이상 경과했다면 즉시 1회 갱신 후 카운트다운을 재시작해야 한다.

### REQ-REFRESH-005 다중 세션 일관성

- **REQ-REFRESH-005 (다른 탭 변경 반영 — Event-driven)**
  WHEN 사용자가 다른 탭/세션에서 새로고침 주기를 변경하면, THEN 현재 탭은 다음 환경설정 조회(`GET /preference`) 시점 또는 갱신 사이클에서 최신 주기를 반영해야 한다(즉시 강제 동기화는 비범위 — REQ-REFRESH-005 는 eventual 일관성).

---

## 6. API 명세

base path: `/api/v1/dashboard`

본 SPEC 은 **신규 엔드포인트를 추가하지 않는다.** SPEC-CMS-DASHBOARD-PERSONALIZE-001 의 기존 GET/PATCH `/preference` 경로에 신규 필드 1개를 수용할 뿐이다.

| Method | Path | 변경점 | REQ |
|---|---|---|---|
| GET | `/preference` | 응답 DTO(`PreferenceResponse`)에 `refreshIntervalSeconds` 추가 | REQ-REFRESH-001-3 |
| PATCH | `/preference` | 요청 DTO(`PreferenceUpdateRequest`)에 `refreshIntervalSeconds`(nullable) 부분 갱신 필드 추가 | REQ-REFRESH-001-1,2 |

위젯 데이터 자동 갱신은 SPEC-CMS-008 의 기존 `GET /widgets/{id}/data` 를 그대로 호출한다(신규 백엔드 없음).

PATCH 요청 예시:

```json
{ "refreshIntervalSeconds": 300 }
```

OFF 설정 예시 (명시적 null 허용):

```json
{ "refreshIntervalSeconds": null }
```

GET 응답 예시 (신규 필드만 발췌):

```json
{
  "userId": 42,
  "theme": "DARK",
  "refreshIntervalSeconds": 300,
  "updatedAt": "2026-06-01T10:00:00Z"
}
```

---

## 7. 기술 접근 (구현 전략)

### 7.1 백엔드 (additive)

- **엔티티**: `UserDashboardPreference` 에 `private Integer refreshIntervalSeconds;` 추가 (Integer 로 NULL 표현). `defaults()` 는 미지정 → null.
- **DTO**: `PreferenceResponse` / `PreferenceUpdateRequest` 에 `Integer refreshIntervalSeconds` 추가. PATCH 부분 갱신 규약상 "필드 부재 = 미변경, 명시적 null = OFF" 를 구분(예: `JsonNullable` 또는 별도 presence 플래그 — research.md 에서 결정).
- **검증**: 서비스 `update()` 에서 허용 집합(`null,30,60,300,900,1800`) 화이트리스트 검증 후 저장. DB CHECK 제약과 이중 방어.
- **매퍼**: `UserDashboardPreferenceMapper.xml` SELECT/UPDATE 컬럼 목록에 `refresh_interval_seconds` 추가.
- 신규 컨트롤러/엔드포인트/서비스 클래스 없음.

### 7.2 프런트엔드 (additive)

- **store** (`dashboardPreferenceStore.ts`): `refreshIntervalSeconds` 상태 + 설정 액션 추가. 주기 변경 시 PATCH 호출(기존 패널 영속화 패턴 재사용, debounce 기존 규약 따름).
- **API** (`api/dashboardPreference.ts`): PATCH 페이로드/GET 매핑에 신규 필드 포함.
- **패널** (`DashboardPreferencePanel.vue`): 주기 선택 컨트롤(셀렉트/세그먼트) 1개 추가. 옵션 라벨은 i18n 키로(OFF/30초/1분/5분/15분/30분).
- **타이머 컴포저블** (신규 1개, 예: `useDashboardAutoRefresh.ts`):
  - `refreshIntervalSeconds` 를 watch 하여 `setInterval` 시작/정지
  - 매 tick 마다 표시 중(숨김 제외) 위젯의 `fetchData()` 호출 — 기존 위젯 데이터 로직 재사용
  - Page Visibility API(`visibilitychange`) 구독으로 일시정지/재개
  - 카운트다운 인디케이터용 `secondsRemaining` 반응형 값 노출
  - 언마운트 시 인터벌/리스너 정리(누수 방지)
- **인디케이터 컴포넌트** (신규 1개): `secondsRemaining` 표시 + "지금 새로고침" 액션.

### 7.3 영향 범위 (3+ 파일 → 논리 단위 분할)

| 단위 | 파일 | 변경 |
|---|---|---|
| DB | `V42__...refresh_interval.sql` | 신규 |
| 백엔드 도메인 | entity / 2 DTO / serviceImpl / mapper.xml | additive 필드 |
| 프런트 상태 | store / api 모듈 | additive 필드 |
| 프런트 UI | 패널 + 타이머 컴포저블(신규) + 인디케이터(신규) | 추가 |

---

## 8. 비기능 요구사항

| 항목 | 임계값 | 측정 |
|---|---|---|
| `PATCH /preference` (주기 변경) p95 | < 100ms | 단행 UPDATE (기존 경로 재사용) |
| 자동 갱신 1사이클 (위젯 ≤ 9) 체감 | 전체 리로드 없이 부분 갱신 | UX 관찰 |
| 카운트다운 표시 갱신 | 1초 단위 | requestAnimationFrame/setInterval |
| 비가시 탭 백그라운드 API 호출 | 0건 | Page Visibility 일시정지 검증 |
| 타이머/리스너 누수 | 라우트 이탈·언마운트 시 0건 | 메모리/리스너 검사 |

---

## 9. 권한 매트릭스

| 역할 | 본인 refresh_interval_seconds |
|---|---|
| SUPER_ADMIN | C/R/U |
| DEPT_ADMIN | C/R/U |
| EDITOR | C/R/U |
| VIEWER | C/R/U |

→ 본 SPEC 의 모든 기능은 **본인 데이터에 한정**(부모 SPEC 의 본인 한정 원칙 계승). 타인 주기 조회/수정 API 없음. 기존 `@AuthenticationPrincipal(expression="userId")` + `@PreAuthorize("isAuthenticated()")` 그대로 적용.

---

## 10. 위험 및 대응

| ID | 위험 | 영향 | 대응 |
|---|---|---|---|
| RISK-DR-01 | 짧은 주기(30s) + 다수 위젯 → API 부하 급증 | 서버 부하 | 위젯별 동시 호출 제한, 숨김 위젯 제외(REQ-REFRESH-002-2), 최소 주기 30s 하한 |
| RISK-DR-02 | 컴포넌트 언마운트 시 인터벌/리스너 미정리 | 메모리 누수·유령 호출 | 컴포저블에서 onUnmounted 정리 의무화(REQ 비기능) |
| RISK-DR-03 | PATCH 부분 갱신에서 "필드 부재" vs "명시적 null(OFF)" 모호 | OFF 설정 불가 | DTO presence 구분(JsonNullable 등) research.md 에서 결정 |
| RISK-DR-04 | 비가시 → 가시 복귀 시 중복 갱신 트리거 | 불필요 호출 | 가시 복귀 시 1회만 즉시 갱신(REQ-REFRESH-004-2) |
| RISK-DR-05 | 자동 갱신 중 사용자 편집모드(부모 SPEC DnD)와 충돌 | 위치 깜빡임/조작 방해 | 편집 모드 활성 시 자동 갱신 일시정지(연계 규칙, research.md 확인) |

---

## 11. 외부 의존성

- **신규 프론트엔드 라이브러리**: 없음 (Page Visibility API 는 브라우저 표준, `setInterval` 표준). 기존 ECharts setOption 재사용.
- **백엔드 신규 의존성**: 없음 (기존 MyBatis + PostgreSQL 재사용).
- **i18n 키 신규**: 약 8개 (주기 옵션 라벨 6 + "지금 새로고침" + 갱신 실패 안내).

---

## 12. 범위 및 비범위

### 12.1 범위 (포함)

- 사용자별 새로고침 주기 1종 저장/복원 (OFF/30s/1m/5m/15m/30m)
- 위젯 데이터 부분 자동 갱신 (전체 리로드 금지)
- 카운트다운 인디케이터 + 수동 즉시 새로고침
- Page Visibility 기반 일시정지/재개

### 12.2 비범위 (제외)

| 비범위 | 결정 | 향후 검토 |
|---|---|---|
| 위젯별 개별 주기 | 대시보드 전역 단일 주기로 충분 | v0.2+ |
| 레이아웃별 서로 다른 주기 | 사용자 1행 단일 값 유지(단순성) | 별도 SPEC |
| 서버 푸시/SSE/WebSocket 실시간 갱신 | 폴링 타이머로 충분, 인프라 추가 회피 | 별도 SPEC |
| 새 위젯/차트/KPI/데이터 출처 | SPEC-CMS-008 기존 페치 재사용 | 없음 |
| 신규 백엔드 엔드포인트 | 기존 PATCH `/preference` 재사용 | 없음 |
| 부서/조직 단위 강제 주기 정책 | 사용자 자율 영역 | 정책 변경 없음 |
| 주기 변경 이력 audit | PII 무관 + 사용자 자율 | 없음 |
| 모바일 백그라운드 갱신 | 모바일 비가시 시 일시정지로 충분 | 없음 |
| 30초 미만 주기 | 서버 부하 + 실효성 낮음 | 하한 고정 |

---

## 13. Exclusions (What NOT to Build)

본 SPEC 이 의도적으로 다루지 않는 항목:

- **신규 백엔드 엔드포인트**: 기존 GET/PATCH `/api/v1/dashboard/preference` 에 필드 1개만 추가. 새 컨트롤러·새 경로 없음.
- **신규 데이터 출처/위젯/차트**: 갱신은 SPEC-CMS-008 의 기존 `GET /widgets/{id}/data` 만 호출.
- **서버 측 실시간 푸시(SSE/WebSocket)**: 클라이언트 폴링 타이머로 한정. 실시간 인프라 도입 비범위.
- **위젯별/레이아웃별 개별 주기**: 사용자 단일 전역 주기만. per-widget/per-layout 주기는 비범위.
- **30초 미만 고빈도 갱신**: 최소 주기 30초 하한. 그 미만은 서버 부하로 제외.
- **부서/조직 강제 주기 정책**: 사용자 자율 침해. 정책 변경 없음.
- **강제 즉시 다중 세션 동기화**: REQ-REFRESH-005 는 eventual 일관성. 다른 탭 변경의 실시간 강제 반영은 비범위.
- **주기 변경 audit 로그**: 사용자 자율 + PII 무관으로 audit 대상 아님.
- **모바일 백그라운드 자동 갱신**: 비가시 시 일시정지 정책으로 충분, 백그라운드 동작 비범위.

---

## 14. 참조 문서

| 문서 | 절 | 핵심 |
|---|---|---|
| SPEC-CMS-DASHBOARD-PERSONALIZE-001 v0.1 | §5 데이터 모델 | `user_dashboard_preference` 1:1 테이블 재사용 |
| SPEC-CMS-DASHBOARD-PERSONALIZE-001 v0.1 | §7 API | GET/PATCH `/preference` 부분 갱신 경로 재사용 |
| SPEC-CMS-DASHBOARD-PERSONALIZE-001 v0.1 | hidden_widget_instance_ids | 숨김 위젯 갱신 제외 연계(REQ-REFRESH-002-2) |
| SPEC-CMS-008 v0.5 | 위젯 데이터 API | `GET /widgets/{id}/data` 부분 갱신 출처 |
| SPEC-CMS-002 | §8 권한 | 사용자 식별 (`@AuthenticationPrincipal`) |
| backend `db/migration/V41__bbs_post_i18n.sql` | 최신 마이그레이션 | V42 추가 위치 결정 근거 |

---

## 15. 검증 체크리스트

- [ ] V42 마이그레이션이 단일 파일이며 V41 다음 번호
- [ ] `refresh_interval_seconds` 가 nullable + DEFAULT NULL + CHECK 제약 (백필 불요)
- [ ] 신규 백엔드 엔드포인트 0개 (기존 PATCH `/preference` 재사용)
- [ ] 자동 갱신이 전체 페이지 리로드를 수행하지 않음 (부분 갱신만)
- [ ] 숨김 위젯은 자동 갱신에서 제외 (부모 SPEC 연계)
- [ ] 비가시 탭에서 백그라운드 API 호출 0건 (Page Visibility 일시정지)
- [ ] 타이머/리스너 언마운트 시 정리 (누수 0)
- [ ] EARS 5 패턴 사용 (Ubiquitous/Event-driven/State-driven/Unwanted/Optional)
- [ ] 모든 기능이 본인 데이터에만 접근
- [ ] 부모 SPEC 의 어떤 컬럼/API 도 변경하지 않음 (additive only)
- [ ] Exclusions 절에 최소 5개 항목 명시 (현재 9개)

---

## 16. Acceptance Criteria 요약 (상세 시나리오는 acceptance.md)

| ID | 요약 | 연계 REQ |
|---|---|---|
| AC-DR-001 | 주기 선택 시 `refresh_interval_seconds` 가 PATCH 로 영속화된다 | REQ-REFRESH-001-1 |
| AC-DR-002 | 허용 집합 외 값(예: 10)은 400 으로 거부된다 | REQ-REFRESH-001-2 |
| AC-DR-003 | 대시보드 로드 시 저장된 주기로 타이머가 시작된다 (NULL 이면 미시작) | REQ-REFRESH-001-3 |
| AC-DR-004 | 주기 도달 시 위젯 데이터만 부분 갱신되고 전체 리로드는 발생하지 않는다 | REQ-REFRESH-002-1 |
| AC-DR-005 | 숨김 위젯은 자동 갱신 대상에서 제외된다 | REQ-REFRESH-002-2 |
| AC-DR-006 | 일부 위젯 갱신 실패가 다른 위젯 갱신을 중단시키지 않는다 | REQ-REFRESH-002-3 |
| AC-DR-007 | 카운트다운 인디케이터가 남은 시간을 1초 단위로 표시한다 | REQ-REFRESH-003-1 |
| AC-DR-008 | "지금 새로고침" 클릭 시 즉시 갱신 후 카운트다운이 재설정된다 | REQ-REFRESH-003-2 |
| AC-DR-009 | 탭이 비가시가 되면 타이머가 일시정지되어 백그라운드 호출이 없다 | REQ-REFRESH-004-1 |
| AC-DR-010 | 탭이 가시로 복귀하면 타이머가 재개되고, 주기 경과 시 1회 즉시 갱신된다 | REQ-REFRESH-004-2 |
