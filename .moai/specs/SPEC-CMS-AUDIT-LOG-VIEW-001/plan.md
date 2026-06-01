---
id: SPEC-CMS-AUDIT-LOG-VIEW-001
version: 0.1.0
status: Draft
created: 2026-06-01
updated: 2026-06-01
author: manager-spec
priority: P1
---

# 구현 계획 — SPEC-CMS-AUDIT-LOG-VIEW-001

## 개요

SPEC-CMS-005가 제공하는 감사 로그 백엔드 API를 소비하는 프론트엔드 조회 화면을 완성한다. 백엔드·DB 변경은 없으며, Vue 3 + Pinia 인프라를 재사용한다.

## 기술 접근법

- Composition API + Pinia 스토어 단일 진실원.
- 기존 `api/audit.ts` 호출 패턴 확장, export는 blob 처리.
- 백엔드 RBAC를 단일 진실로 신뢰, 프론트는 403만 우아하게 처리.
- 신규 DB 마이그레이션 없음.

## 마일스톤 (우선순위 기반, 시간 추정 없음)

### M1 (Priority High) — API 계층
- `api/audit.ts`에 systemAuditLog 함수 4종 추가.
- 응답/요청 타입 정의 (`AuditLogEntry`, 필터 파라미터 타입).
- 의존: 없음. 선행 작업.

### M2 (Priority High) — Pinia 스토어
- `stores/auditLog.ts` 신규: state(logs, total, page, size, loading, filters, criticalLogs) + actions(fetchLogs, fetchCritical, applyFilter, resetFilter, exportCsv).
- 의존: M1.

### M3 (Priority High) — 뷰 완성
- `AuditLogView.vue`: CRITICAL 패널(세션 dismiss), 필터 패널, 결과 테이블, 상세 드로어(JSON diff), 페이지네이션+크기 선택, CSV 버튼, 빈 상태.
- 스토어 구독만으로 렌더링.
- 의존: M2.

### M4 (Priority Medium) — i18n
- `system.auditLog.*` 키를 ko.json + en.json에 추가.
- 의존: M3 (사용 키 확정 후).

### M5 (Priority Medium) — 테스트
- `tests/stores/auditLog.spec.ts`: fetchLogs / applyFilter / exportCsv mock 테스트.
- 의존: M2.

## 기술 위험 및 완화

| 위험 | 영향 | 완화 |
|------|------|------|
| 기존 `api/audit.ts` / 스토어 패턴 미정렬 | 코드 리뷰 반려, 일관성 저하 | Run 단계에서 notificationCenter 스토어·기존 audit.ts 정독 후 컨벤션 매칭 |
| before/after JSONB 구조 다양성(null/대형) | 드로어 diff 렌더 깨짐 | null 측 명시 표기, 대형 JSON 접기/스크롤 처리 |
| CSV blob 다운로드 브라우저 호환 | 다운로드 실패 | 표준 blob + objectURL + anchor download 패턴 사용 |
| CRITICAL dismiss 상태 영속 범위 오해 | 세션 외 지속 또는 즉시 재표시 | sessionStorage 또는 스토어 in-memory 플래그로 세션 한정 보장 |
| 권한 격리 프론트 과복제 | 백엔드 RBAC와 이중 진실 | 프론트는 403 처리만, 권한 판단 로직 복제 금지 |

## 검증 전략

- 스토어 단위 테스트(Vitest, mock).
- 수락 기준 AC-AL-* 수동/통합 검증.
- 권한: PUBLIC_USER 토큰 → 403 확인.
