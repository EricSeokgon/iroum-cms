---
id: SPEC-CMS-AUDIT-LOG-MULTI-FILTER-001
version: 0.1.0
status: Implemented
created: 2026-06-09
updated: 2026-06-09
author: MoAI
priority: P2
parent: SPEC-CMS-AUDIT-LOG-VIEW-001
related:
  - SPEC-CMS-AUDIT-LOG-VIEW-001 (감사 로그 조회 화면 — 멀티셀렉트 UI 원천, 알려진 제약)
  - SPEC-CMS-005 (audit_log 테이블 및 백엔드 조회/내보내기 API 원천)
issue_number: TBD
---

# SPEC-CMS-AUDIT-LOG-MULTI-FILTER-001 — 감사 로그 다중값 필터 (action / severity)

## HISTORY

- 2026-06-09 (v0.1.0): Draft 작성. 부모 SPEC-CMS-AUDIT-LOG-VIEW-001의 알려진 제약(백엔드 단일값 필터) 해소. 백엔드 단일 도메인 enhancement.

---

## 1. 개요 (Background)

### 1.1 목적

관리자 감사 로그 조회 화면에서 `action`(행위)과 `severity`(심각도) 필터에 **여러 값을 동시에 적용**할 수 있도록 백엔드 API와 프론트엔드 연동을 확장한다.

### 1.2 배경 — 부모 SPEC의 알려진 제약

- SPEC-CMS-AUDIT-LOG-VIEW-001에서 프론트엔드는 `action`/`severity`를 **멀티셀렉트 UI**로 이미 구현했다 (`AuditLogView.vue`, 스토어 상태는 `action: AuditAction[]` / `severity: AuditSeverity[]` 배열).
- 그러나 백엔드 `GET /api/v1/system/audit-logs`는 `action`/`severity`를 **단일 `String` 파라미터**로만 수신한다. 따라서 스토어의 `buildFilter()`는 다중 선택 중 **첫 번째 값만**(`filters.action[0]`, `filters.severity[0]`) 전송하고 나머지는 버린다.
- 이 한계는 CHANGELOG v2.3.0 및 부모 SPEC HISTORY(v0.2.0)에 알려진 제약으로 명시되어 있다.
- 본 SPEC은 이 gap을 메우는 **백엔드 중심 enhancement**다. UI 컴포넌트는 변경하지 않으며, 스토어/API 클라이언트의 전송 로직만 보정한다.

### 1.3 현재 코드 구조 (정확한 사실)

> **주의:** 별도의 `AuditLogSearchRequest` DTO는 **존재하지 않는다.** 필터는 컨트롤러의 위치 기반 `@RequestParam`으로 받아 `AuditLogMapper`의 `search` / `countSearch` / `searchForExport` 3개 메서드로 직접 전달된다. 따라서 본 SPEC의 변경 지점은 "DTO 필드 변경"이 아니라 **컨트롤러 파라미터 + 매퍼 시그니처 + XML WHERE 절**이다.

| 계층 | 위치 | 현재 상태 |
|------|------|-----------|
| 컨트롤러 | `domain/audit/controller/AuditLogController.java` | `search`, `export` 모두 `@RequestParam(required=false) String action`, `String severity` |
| 매퍼 IF | `domain/audit/repository/AuditLogMapper.java` | `search`, `countSearch`, `searchForExport` 모두 `@Param("action") String`, `@Param("severity") String` |
| 매퍼 XML | `mybatis/mapper/audit/AuditLogMapper.xml` | 공통 `<sql id="whereClause">` — `<if test="action != null and action != ''"> AND action = #{action}` (severity 동일) |
| 프론트 타입 | `frontend/admin/src/api/system.ts` | `AuditLogFilter.action?: AuditAction`, `severity?: AuditSeverity` (단일값) |
| 프론트 스토어 | `frontend/admin/src/stores/auditLog.ts` | `buildFilter()`가 `filters.action[0]` / `filters.severity[0]`로 첫 값만 전송 |

---

## 2. 요구사항 (Requirements — EARS)

### REQ-ALF-001 (Ubiquitous — 다중값 수신)

The 감사 로그 조회 API shall `action`과 `severity` 파라미터에 대해 **0개 이상의 다중 값**을 수신할 수 있어야 한다. 다중 값은 반복 쿼리 파라미터(`?action=LOGIN&action=LOGOUT`) 형식으로 전달되며, 이는 Spring MVC의 `List<String>` 바인딩과 axios 기본 배열 직렬화로 자연 지원된다.

### REQ-ALF-002 (Event-Driven — IN 절 적용)

WHEN `action`(또는 `severity`)에 2개 이상의 값이 전달되면, THEN the 시스템 shall SQL WHERE 절에서 해당 컬럼에 `IN (...)` 조건을 적용하여 **전달된 값 중 하나라도 일치하는** 레코드를 반환해야 한다. 동일한 다중값 조건은 `search`(목록), `countSearch`(총건수), `searchForExport`(CSV)에 **동일하게** 적용되어야 한다(단일 공통 `whereClause` 재사용).

### REQ-ALF-003 (Unwanted Behavior — 하위 호환)

IF `action`(또는 `severity`)에 단일 값만 전달되거나 값이 전달되지 않으면, THEN the 시스템 shall 부모 SPEC 시점과 **동일한 결과**를 반환해야 한다(단일값 = 1개 요소 IN 절과 동치, 미전달 = 조건 무시). 기존 단일값 호출자 및 테스트는 변경 없이 통과해야 한다.

---

## 3. 인수 기준 (Acceptance Criteria)

> Given-When-Then 형식. 최소 시나리오는 `acceptance.md`에서 확장한다.

### AC-ALF-001 — 다중 action 필터 (목록)

- **Given** `audit_log`에 action=LOGIN, action=LOGOUT, action=DELETE 레코드가 각각 존재할 때
- **When** `GET /api/v1/system/audit-logs?action=LOGIN&action=LOGOUT` 을 호출하면
- **Then** 응답 `items`에는 LOGIN과 LOGOUT 레코드만 포함되고 DELETE는 제외되며, `total`은 두 조건 합계와 일치한다.

### AC-ALF-002 — 다중 severity + result 동시 필터

- **Given** severity=CRITICAL/WARN/INFO, result=SUCCESS/FAILURE 조합 레코드가 존재할 때
- **When** `GET /api/v1/system/audit-logs?severity=CRITICAL&severity=WARN&result=FAILURE` 을 호출하면
- **Then** (severity ∈ {CRITICAL, WARN}) AND (result = FAILURE) 를 모두 만족하는 레코드만 반환된다. (부모 AC-AL-002-2의 다중값 확장)

### AC-ALF-003 — 단일값 하위 호환 (회귀)

- **Given** 기존 단일값 호출 `?severity=CRITICAL` 이 주어졌을 때
- **When** 변경된 API를 호출하면
- **Then** 부모 SPEC 시점과 동일하게 severity=CRITICAL 레코드만 반환되며, 기존 단위/IT 테스트가 수정 없이 통과한다.

### AC-ALF-004 — CSV 내보내기 다중값 상속

- **Given** 화면에서 action 다중 선택 후 CSV 내보내기를 실행할 때
- **When** `GET /api/v1/system/audit-logs/export?action=LOGIN&action=LOGOUT` 가 호출되면
- **Then** 스트리밍된 CSV에는 LOGIN/LOGOUT 레코드만 포함된다. (스토어 `exportCsv()`가 `buildFilter()` 상태를 재사용하므로 동일 필터가 자동 적용됨)

### AC-ALF-005 — 프론트 전송 보정

- **Given** 스토어 `filters.action = ['LOGIN', 'LOGOUT']` 일 때
- **When** `fetchLogs()` 가 호출되면
- **Then** API 요청 파라미터에 두 action 값이 모두 포함된다(첫 값 절단 없음).

---

## 4. 기술 접근 (Technical Approach)

> WHAT/WHY 중심. 상세 구현은 run 단계로 위임하되, 실제 변경 지점을 명시한다.

### 4.1 백엔드

1. **컨트롤러** (`AuditLogController.search`, `AuditLogController.export`): `@RequestParam(required=false) String action` → `List<String> action`, `String severity` → `List<String> severity`. Spring MVC는 반복 파라미터를 `List`로 바인딩하며, 단일/미전달도 안전 처리.
2. **매퍼 IF** (`AuditLogMapper`): `search`, `countSearch`, `searchForExport` 의 `@Param("action") String` → `List<String>`, `@Param("severity") String` → `List<String>`.
3. **매퍼 XML** (`AuditLogMapper.xml` 공통 `whereClause`): `action`/`severity`의 `<if>` 단일 등치 조건을 **비어있지 않은 컬렉션** 검사 + `<foreach>` `IN (...)` 로 교체. `whereClause`는 search/count/export가 공유하므로 한 곳 수정으로 3개 경로 동시 반영.

### 4.2 프론트엔드

4. **타입** (`api/system.ts` `AuditLogFilter`): `action?: AuditAction` → `action?: AuditAction[]`, `severity?: AuditSeverity` → `severity?: AuditSeverity[]`.
5. **스토어** (`stores/auditLog.ts` `buildFilter()`): `filters.action[0]` → `filters.action`(빈 배열은 `undefined`), severity 동일. CSV `exportCsv()`는 `buildFilter()` 재사용으로 자동 상속.
6. axios 기본 배열 직렬화(반복 파라미터)를 사용하며, 별도 직렬화 옵션 변경이 필요한지 run 단계에서 확인한다.

### 4.3 비변경 원칙

- DB 스키마/마이그레이션 변경 없음 (`audit_log` 테이블 불변, append-only 유지).
- UI 컴포넌트(`AuditLogView.vue`) 멀티셀렉트 마크업 변경 없음.
- 인증/인가(`@PreAuthorize`) 변경 없음.

---

## 5. Exclusions (What NOT to Build)

- **전문 검색(full-text search):** 자유 텍스트 검색은 본 SPEC 범위 밖. action/severity 다중값에 한정한다.
- **다른 필터의 다중값화:** `result`, `entity_type`, `actorId` 등 나머지 필터는 단일값 유지. 본 SPEC은 `action`/`severity` 2개에만 한정한다.
- **CSV 내보내기 전용 다중필터 로직:** 별도 구현하지 않는다. CSV는 스토어 `applyFilter` 상태(`buildFilter()`)를 재사용하므로 동일 수정으로 **자동 상속**된다.
- **프론트 enum 정합성 보정:** 프론트 `AuditAction` enum과 백엔드 action 열거형의 항목 차이(예: 백엔드의 LOGIN_FAILURE, PERMISSION_DENIED 등 누락)는 본 SPEC에서 다루지 않는다.
- **신규 DB 인덱스/성능 튜닝:** IN 절 대상 컬럼 인덱스 추가는 범위 밖. 필요 시 별도 SPEC.
