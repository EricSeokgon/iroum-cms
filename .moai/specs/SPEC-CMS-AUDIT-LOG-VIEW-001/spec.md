---
id: SPEC-CMS-AUDIT-LOG-VIEW-001
version: 0.4.0
status: Completed
created: 2026-06-01
updated: 2026-06-09
author: manager-spec
priority: P1
parent: SPEC-CMS-005 v0.2.1
related:
  - SPEC-CMS-005 (audit_log 테이블 및 백엔드 API 원천)
  - SPEC-CMS-002 (RBAC — SUPER_ADMIN/ADMIN/DEPT_ADMIN AUDIT:READ 권한)
issue_number: TBD
---

# SPEC-CMS-AUDIT-LOG-VIEW-001 — 관리자 감사 로그 조회 화면

## HISTORY

- 2026-06-09 (v0.3.0): Tested. CI GREEN (origin/main 6dc5e24) — 단위 테스트 9/9, 전체 71/71 GREEN. CHANGELOG v2.3.0 동기화 완료.
- 2026-06-01 (v0.2.0): Implemented. TDD 완료 — 단위 테스트 9/9 GREEN (전체 71/71 GREEN). 주의: 백엔드 action/severity 필터가 단일값만 지원, 프론트 멀티셀렉트 첫 번째 값만 전송 (백엔드 확장 필요시 SPEC-CMS-005 업데이트 필요).
- 2026-06-01 (v0.1.0): Draft 작성. SPEC-CMS-005 백엔드 API를 소비하는 프론트엔드 조회 화면 정의. 신규 DB 마이그레이션 없음, Vue 3 + Pinia 인프라 재사용.

---

## 1. 개요

### 1.1 목적

관리자가 시스템 전체의 감사 로그(audit log)를 단일 화면에서 조회·필터링·CSV 내보내기할 수 있는 통합 UI를 제공한다. 보안 사고 추적, 권한 변경 감사, 운영 이상 징후(CRITICAL 이벤트) 즉시 인지를 목적으로 한다.

### 1.2 배경

- SPEC-CMS-005에서 `audit_log` 테이블과 조회/내보내기 백엔드 API가 **이미 완전히 구현**되어 있다.
- 프론트엔드는 라우트(`system/audit-logs`)와 `AuditLogView.vue` 스텁만 등록된 상태로, API 연동·상태관리·완성된 UI가 부재하다.
- 본 SPEC은 이 gap을 메우는 **프론트엔드 전용 작업**을 정의한다.

### 1.3 범위

프론트엔드 4개 산출물: API 클라이언트 함수, Pinia 스토어, 화면 완성, 단위 테스트. 백엔드·DB 변경은 일절 없다.

---

## 2. SPEC-CMS-005가 이미 제공하는 것 (재사용 인프라)

| 구분 | 항목 | 비고 |
|------|------|------|
| API | `GET /api/v1/system/audit-logs` | 필터(action, entity_type, severity, result, actorId, fromTime, toTime, page, size) 검색 |
| API | `GET /api/v1/system/audit-logs/{id}` | 단건 조회 |
| API | `GET /api/v1/system/audit-logs/critical` | CRITICAL 이벤트 (limit 파라미터) |
| API | `GET /api/v1/system/audit-logs/export` | CSV 스트리밍 다운로드 |
| 인증 | `@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('SYSTEM:AUDIT')")` | 백엔드 RBAC 강제 |
| 도메인 | `AuditLog` 엔티티 | id, event_time, actor_id, actor_role, action, entity_type, entity_id, before_value(JSONB), after_value(JSONB), ip_address, user_agent, trace_id, severity(INFO/WARN/CRITICAL), result(SUCCESS/FAILURE), failure_reason, duration_ms |
| 도메인 | action 열거형 | CREATE/READ/UPDATE/DELETE/LOGIN/LOGIN_FAILURE/LOGOUT/PERMISSION_CHANGE/PERMISSION_DENIED/PASSWORD_CHANGE/PASSWORD_RESET/TOKEN_REFRESH/TOKEN_REVOKE/EXPORT/BATCH |

## 3. 본 SPEC이 신규 도입하는 것 (gap)

| 산출물 | 현재 상태 | 신규/완성 |
|--------|-----------|-----------|
| `frontend/admin/src/api/system.ts` | `auditLogs` 객체 이미 존재 (search/findById/export/critical 함수 구현됨) | **변경 없음** — 기존 API 재사용 |
| `frontend/admin/src/stores/auditLog.ts` | 부재 | Pinia 스토어 신규 |
| `frontend/admin/src/views/system/AuditLogView.vue` | 스텁 (CRITICAL/필터 골격만, API·스토어 미연동) | 완성 |
| `tests/stores/auditLog.spec.ts` | 부재 | 단위 테스트 신규 |
| i18n 키 (`system.auditLog.*`) | 부분/부재 | ko.json + en.json 키 추가 |

---

## 4. 범위 및 비범위

### 4.1 범위 (In Scope)

- 시스템 감사 로그 목록 조회 화면 (필터 + 테이블 + 페이지네이션)
- CRITICAL 알림 패널 (세션 dismiss)
- 행 클릭 상세 드로어 (before/after JSON diff)
- CSV 내보내기 (현재 필터 적용)
- Pinia 스토어 기반 상태 관리
- ko/en i18n 키
- 스토어 단위 테스트

### 4.2 비범위 (Exclusions — What NOT to Build)

- **백엔드 API·컨트롤러·서비스·MyBatis 매퍼 변경** — SPEC-CMS-005에서 완료됨, 일절 수정 금지
- **신규 DB 마이그레이션 / `audit_log` 스키마 변경** — 없음
- **백엔드 RBAC `@PreAuthorize` 규칙 변경** — 기존 규칙 그대로 소비
- **감사 로그 생성(write) 로직** — 본 SPEC은 조회(read) 전용
- **로그 보존(retention)·아카이빙·삭제 기능** — 별도 SPEC 대상
- **실시간 푸시/웹소켓 스트리밍** — 본 SPEC은 폴링/수동 새로고침 기반
- **차트·통계 대시보드 시각화** — 본 SPEC은 표 기반 조회만
- **`personal-data-access` / `login-history` 기존 화면 수정** — 본 SPEC과 무관

---

## 5. 신규 요구사항 (REQ-AL-*) — EARS 형식

### REQ-AL-001 — 시스템 감사 로그 목록 조회 (Ubiquitous)
The system shall display a paginated list of system audit logs retrieved from `GET /api/v1/system/audit-logs`, showing event_time, actor (id + role), action, entity_type/entity_id, severity, result, and duration_ms for each row.

### REQ-AL-002 — 다중 필터 (Event-Driven)
When an administrator applies one or more filter criteria (action multi-select, severity multi-select, result, date range, actor user ID), the system shall query the audit-logs endpoint with the corresponding parameters and render only matching records.

### REQ-AL-003 — CRITICAL 알림 패널 (State-Driven)
While CRITICAL-severity audit events exist (retrieved via `GET /api/v1/system/audit-logs/critical`), the system shall display a dismissible alert panel at the top of the view summarizing them.

### REQ-AL-003a — CRITICAL 패널 세션 dismiss (Event-Driven / Unwanted)
When the administrator dismisses the CRITICAL panel, the system shall hide it for the remainder of the browser session; if the view is re-entered within the same session, then the system shall not re-display the dismissed panel.

### REQ-AL-004 — 상세 드로어 (Event-Driven)
When an administrator clicks a result-table row, the system shall open a detail drawer showing the full audit record including a readable before_value / after_value JSON diff.

### REQ-AL-005 — CSV 내보내기 (Event-Driven)
When the administrator activates the CSV export action, the system shall call `GET /api/v1/system/audit-logs/export` with the currently applied filter parameters and trigger a browser file download named `audit-logs-YYYY-MM-DD.csv`.

### REQ-AL-006 — 페이지네이션 + 크기 선택 (Event-Driven)
When the administrator changes the page or the page-size selector (20 / 50 / 100), the system shall re-fetch the corresponding page from the audit-logs endpoint and update the table and pagination controls.

### REQ-AL-007 — 권한 격리 (Unwanted / State-Driven)
If a request is made by a principal without ADMIN, SUPER_ADMIN, DEPT_ADMIN role or the SYSTEM:AUDIT authority, then the backend shall reject it with HTTP 403; while a non-authorized user navigates to the route, the frontend shall not expose audit data and shall surface an access-denied state.

### REQ-AL-008 — Pinia 스토어 기반 상태 관리 (Ubiquitous)
The system shall manage all audit-log view state (logs, total, page, size, loading, filters, criticalLogs) in a dedicated Pinia store (`stores/auditLog.ts`), and the view shall derive its rendering exclusively from that store.

### REQ-AL-009 — 로딩 및 빈 상태 (State-Driven)
While an audit-log request is in flight, the system shall display a loading indicator; while a completed query returns zero records, the system shall display an empty-state illustration instead of an empty table.

### REQ-AL-010 — 필터 초기화 (Event-Driven)
When the administrator triggers filter reset, the system shall clear all filter fields to their defaults and re-fetch the unfiltered first page.

### REQ-AL-011 — 오류 처리 (Unwanted)
If an audit-log API call fails (network error or non-2xx response), then the system shall surface a non-blocking error message, clear the loading state, and preserve the previously displayed data where available.

### REQ-AL-012 — 국제화 (Ubiquitous)
The system shall render all labels, action/severity/result badges, and messages through i18n keys under `system.auditLog.*` provided in both ko.json and en.json.

---

## 6. 수락 기준 (AC-AL-*)

### REQ-AL-001
- **AC-AL-001-1**: 화면 진입 시 첫 페이지 목록이 자동 로드되어 테이블에 표시된다.
- **AC-AL-001-2**: 각 행에 event_time, actor(id+role), action 배지, entity_type/id, severity 배지, result 배지, duration_ms가 표시된다.

### REQ-AL-002
- **AC-AL-002-1**: 50건 데이터에서 action=LOGIN 필터 적용 시 LOGIN 건만 표시된다.
- **AC-AL-002-2**: severity=CRITICAL + result=FAILURE 동시 필터 시 두 조건을 모두 만족하는 건만 표시되고, API 호출 파라미터에 두 값이 포함된다.
- **AC-AL-002-3**: date range(fromTime/toTime) 지정 시 해당 기간 파라미터가 API 호출에 포함된다.

### REQ-AL-003 / REQ-AL-003a
- **AC-AL-003-1**: CRITICAL 로그 존재 시 상단 패널이 표시된다.
- **AC-AL-003-2**: 패널 dismiss 후 같은 세션 내 재진입 시 패널이 다시 표시되지 않는다.
- **AC-AL-003-3**: CRITICAL 로그가 0건이면 패널이 렌더링되지 않는다.

### REQ-AL-004
- **AC-AL-004-1**: 행 클릭 시 드로어가 열리고 before_value / after_value JSON이 표시된다.
- **AC-AL-004-2**: before/after가 모두 존재하면 변경 필드를 식별 가능한 diff 형태로 보여준다.
- **AC-AL-004-3**: before 또는 after가 null인 경우(CREATE/DELETE 등) 빈 측을 명시적으로 표기한다.

### REQ-AL-005
- **AC-AL-005-1**: CSV 내보내기 시 현재 필터 조건이 export API 호출 파라미터로 전달된다.
- **AC-AL-005-2**: 다운로드 파일명이 `audit-logs-YYYY-MM-DD.csv` 형식이다.
- **AC-AL-005-3**: 응답이 blob으로 처리되어 브라우저 다운로드가 트리거된다.

### REQ-AL-006
- **AC-AL-006-1**: page 변경 시 해당 페이지가 재조회된다.
- **AC-AL-006-2**: size 선택(20/50/100) 변경 시 size 파라미터가 반영되어 재조회되고 페이지가 1로 초기화된다.

### REQ-AL-007
- **AC-AL-007-1**: PUBLIC_USER 토큰으로 audit-logs API 호출 시 백엔드가 403을 반환한다.
- **AC-AL-007-2**: 403 응답 시 프론트엔드가 데이터 대신 접근 거부 상태를 표시한다.

### REQ-AL-008
- **AC-AL-008-1**: 뷰는 로컬 컴포넌트 상태가 아닌 Pinia 스토어에서 logs/total/page/size/loading/filters/criticalLogs를 구독한다.
- **AC-AL-008-2**: fetchLogs / fetchCritical / applyFilter / resetFilter / exportCsv 액션이 스토어에 정의되어 있다.

### REQ-AL-009
- **AC-AL-009-1**: 조회 진행 중 로딩 인디케이터가 표시된다.
- **AC-AL-009-2**: 결과 0건 시 빈 상태 일러스트가 표시된다.

### REQ-AL-010
- **AC-AL-010-1**: 필터 초기화 시 모든 필터가 기본값으로 돌아가고 첫 페이지 무필터 결과가 재조회된다.

### REQ-AL-011
- **AC-AL-011-1**: API 실패 시 오류 메시지가 표시되고 loading 상태가 해제된다.

### REQ-AL-012
- **AC-AL-012-1**: ko.json과 en.json 양쪽에 `system.auditLog.*` 키가 존재하며 하드코딩 문자열이 없다.

---

## 7. 기술 접근법

- **프레임워크**: Vue 3 (Composition API) + Pinia (기존 스토어 패턴 재사용 — `stores/notificationCenter.ts` 참조).
- **API 계층**: `api/system.ts`의 기존 `auditLogs` 객체(search/findById/export/critical) 재사용. 추가 구현 없음.
- **상태 관리**: `stores/auditLog.ts` 신규. 뷰는 스토어 구독만 수행.
- **DB**: 신규 마이그레이션 없음. `audit_log` 스키마/MyBatis 매퍼는 SPEC-CMS-005 그대로 소비.
- **인증**: 백엔드 `@PreAuthorize`가 단일 진실. 프론트엔드는 403을 우아하게 처리하되 권한 판단을 자체 복제하지 않는다.
- **i18n**: `system.auditLog.*` 네임스페이스, ko/en 동시 추가.
- **테스트**: Vitest + mock 기반 스토어 단위 테스트.

> Run 단계 주의: 구현 에이전트는 코드 작성 전 `stores/notificationCenter.ts`, `views/notifications/NotificationCenterView.vue`, `api/system.ts`(auditLogs 객체), `locales/{ko,en}.json`을 반드시 정독하여 명명·구조·i18n 키 컨벤션을 정렬할 것. `AuditLogView.vue`가 이미 `auditLogs`를 `@/api/system`에서 임포트하므로 API 계층 변경은 불필요하다.

---

## 8. 구현 파일 목록

| 파일 | 작업 |
|------|------|
| `frontend/admin/src/api/system.ts` | `auditLogs` 기존 구현 재사용 (변경 없음) |
| `frontend/admin/src/stores/auditLog.ts` | Pinia 스토어 신규 (state + actions) |
| `frontend/admin/src/views/system/AuditLogView.vue` | 스텁 완성 (CRITICAL 패널, 필터, 테이블, 드로어, 페이지네이션, CSV, 빈 상태) |
| `frontend/admin/src/locales/ko.json` | `system.auditLog.*` 키 추가 |
| `frontend/admin/src/locales/en.json` | `system.auditLog.*` 키 추가 |
| `tests/stores/auditLog.spec.ts` | fetchLogs / applyFilter / exportCsv mock 테스트 |
