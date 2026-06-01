---
id: SPEC-CMS-AUDIT-LOG-VIEW-001
version: 0.1.0
status: Draft
created: 2026-06-01
updated: 2026-06-01
author: manager-spec
priority: P1
---

# 수락 기준 (Given-When-Then) — SPEC-CMS-AUDIT-LOG-VIEW-001

## 시나리오 1 — 목록 조회 (REQ-AL-001)
- **Given** 관리자가 인증된 상태로 `system/audit-logs` 화면에 진입하고
- **When** 화면이 마운트되면
- **Then** 첫 페이지 목록이 자동 로드되어 event_time, actor(id+role), action 배지, entity_type/id, severity 배지, result 배지, duration_ms 컬럼이 표시된다.

## 시나리오 2 — action 필터 (REQ-AL-002)
- **Given** 50건의 혼합 감사 로그가 존재하고
- **When** action=LOGIN 필터를 적용하면
- **Then** LOGIN 건만 표시되고 API 호출 파라미터에 `action=LOGIN`이 포함된다.

## 시나리오 3 — 복합 필터 (REQ-AL-002)
- **Given** 다양한 severity/result 로그가 존재하고
- **When** severity=CRITICAL 과 result=FAILURE 를 동시에 적용하면
- **Then** 두 조건을 모두 만족하는 건만 표시되고 두 파라미터가 API 호출에 포함된다.

## 시나리오 4 — CRITICAL 패널 표시·dismiss (REQ-AL-003 / 003a)
- **Given** CRITICAL severity 이벤트가 존재하고
- **When** 화면에 진입하면
- **Then** 상단 CRITICAL 알림 패널이 표시되며,
- **And When** 관리자가 패널을 dismiss 하고 같은 세션 내 화면을 재진입하면
- **Then** 패널이 다시 표시되지 않는다.

## 시나리오 5 — CRITICAL 0건 (REQ-AL-003)
- **Given** CRITICAL 이벤트가 0건이고
- **When** 화면에 진입하면
- **Then** CRITICAL 패널이 렌더링되지 않는다.

## 시나리오 6 — 상세 드로어 JSON diff (REQ-AL-004)
- **Given** before_value/after_value 가 모두 존재하는 UPDATE 로그가 있고
- **When** 해당 행을 클릭하면
- **Then** 드로어가 열리고 변경 필드를 식별 가능한 before/after diff로 표시한다.

## 시나리오 7 — 드로어 null 측 표기 (REQ-AL-004)
- **Given** before_value 가 null 인 CREATE 로그가 있고
- **When** 행을 클릭하면
- **Then** before 측이 명시적으로 비어있음(예: "(없음)")으로 표기된다.

## 시나리오 8 — CSV 내보내기 (REQ-AL-005)
- **Given** action=EXPORT 필터가 적용된 상태에서
- **When** CSV 내보내기를 실행하면
- **Then** export API가 현재 필터 파라미터를 포함해 호출되고, `audit-logs-YYYY-MM-DD.csv` 파일이 blob으로 다운로드된다.

## 시나리오 9 — 페이지/크기 변경 (REQ-AL-006)
- **Given** 100건 이상 데이터가 있고
- **When** page-size를 50으로 변경하면
- **Then** `size=50` 파라미터로 재조회되고 page가 1로 초기화된다.

## 시나리오 10 — 권한 격리 (REQ-AL-007)
- **Given** PUBLIC_USER 권한 토큰으로
- **When** audit-logs API를 호출하면
- **Then** 백엔드가 HTTP 403을 반환하고, 프론트엔드는 데이터 대신 접근 거부 상태를 표시한다.

## 시나리오 11 — Pinia 스토어 (REQ-AL-008)
- **Given** 뷰가 렌더링될 때
- **When** 상태를 읽으면
- **Then** logs/total/page/size/loading/filters/criticalLogs를 `stores/auditLog.ts`에서 구독하며, 로컬 컴포넌트 상태로 중복 보관하지 않는다.

## 시나리오 12 — 로딩·빈 상태 (REQ-AL-009)
- **Given** 조회가 진행 중이면 로딩 인디케이터가 표시되고
- **When** 결과가 0건으로 완료되면
- **Then** 빈 테이블 대신 빈 상태 일러스트가 표시된다.

## 시나리오 13 — 필터 초기화 (REQ-AL-010)
- **Given** 여러 필터가 적용된 상태에서
- **When** 필터 초기화를 실행하면
- **Then** 모든 필터가 기본값으로 돌아가고 무필터 첫 페이지가 재조회된다.

## 시나리오 14 — API 오류 (REQ-AL-011)
- **Given** 목록 조회 중 서버가 5xx를 반환하면
- **When** 응답이 처리될 때
- **Then** 비차단 오류 메시지가 표시되고 loading이 해제되며 가능 시 이전 데이터가 유지된다.

## 시나리오 15 — i18n (REQ-AL-012)
- **Given** ko/en 로케일에서
- **When** 화면을 렌더링하면
- **Then** 모든 라벨·배지·메시지가 `system.auditLog.*` 키로 출력되고 하드코딩 문자열이 없다.

---

## Edge Cases

- 대형 JSONB(before/after 수천 라인) → 드로어 접기/스크롤.
- duration_ms null → 빈 셀 또는 "-".
- actor_id 가 시스템(자동 배치) → BATCH action 표기.
- date range 역전(from > to) → 검증 메시지.

## Definition of Done

- [ ] REQ-AL-001~012 전부 구현.
- [ ] AC-AL-* 전부 검증 통과.
- [ ] 스토어 단위 테스트(fetchLogs/applyFilter/exportCsv) 통과.
- [ ] ko.json + en.json `system.auditLog.*` 키 완비, 하드코딩 0.
- [ ] 백엔드·DB 무변경 확인.
- [ ] PUBLIC_USER 403 처리 확인.
- [ ] 기존 audit.ts / notificationCenter 스토어 컨벤션 정렬 확인.
