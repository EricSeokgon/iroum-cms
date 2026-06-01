---
id: SPEC-CMS-USER-BULK-STATUS-001
type: plan
version: 0.1.0
updated: 2026-06-01
---

# 구현 계획 — 사용자 일괄 상태 변경

## 1. 기술 접근 요약

기존 `UserController` / `UserService` / `UserStatus`(SPEC-CMS-002)와 감사 로그 인프라(SPEC-CMS-005), 프론트의 `usersApi` / `UserListView.vue`를 재사용한다. 신규 엔드포인트 1개(`PATCH /api/v1/users/bulk-status`)와 서비스 메서드 1개를 추가하고, 프론트에 다중선택 UI와 결과 피드백을 더한다. 핵심 설계 원칙은 **건별 격리 처리**(부분 실패 허용)와 **변경 성공 건별 감사 기록**이다.

## 2. 마일스톤 (우선순위 기반, 시간 추정 없음)

### M1 — 백엔드 API 및 서비스 (Priority: High)
- `BulkStatusUpdateRequest` / `BulkStatusUpdateResponse` DTO 정의 (검증 어노테이션 포함).
- `UserService.bulkUpdateStatus(...)` 인터페이스 선언 + `UserServiceImpl` 구현.
  - 건별 격리 처리 (ID별 try/catch, 실패 항목 수집).
  - 상태 전환 규칙 (REQ-UBS-004): `DELETED` 스킵, `LOCKED→ACTIVE` unlock 동등 처리.
  - `targetStatus=DELETED` 요청의 SUPER_ADMIN 추가 검증.
- `UserController`에 `@PatchMapping("/bulk-status")` 추가.
- 의존: SPEC-CMS-002(UserStatus, RBAC).

### M2 — 감사 로그 통합 (Priority: High)
- 변경 성공 건별 `audit_log` 기록 (action=UPDATE, entity_type=USER, before/after).
- SPEC-CMS-005 패턴 준수 (AuditLogAspect 수동 호출 또는 서비스 내 기록).
- 실패 항목은 미기록 검증.
- 의존: M1, SPEC-CMS-005.

### M3 — 프론트엔드 다중선택 UI (Priority: High)
- `el-table` selection 컬럼 추가 + `@selection-change` 핸들러.
- `DELETED` 행 선택 불가 가드(`:selectable`).
- 선택 카운터 + 일괄 작업 툴바 (대상 상태 선택 + 실행 버튼).
- 100건 초과 선택 경고 및 해제.
- 의존: 없음(M1과 병렬 가능, API 연동은 M1 완료 후).

### M4 — 프론트엔드 API 연동 및 피드백 (Priority: High)
- `usersApi.bulkUpdateStatus()` 함수 추가.
- 확인 다이얼로그(`ElMessageBox.confirm`) — 영향 인원·대상 상태 표시.
- 결과 토스트(성공/실패 건수) + 실패 상세 보기 + 목록 자동 갱신 + 선택 초기화.
- i18n 키 추가.
- 의존: M1, M3.

### M5 — 테스트 (Priority: Medium)
- 백엔드: 정상·부분실패·빈입력·권한·DELETED스킵·감사기록·100건초과 케이스.
- 프론트: 선택/툴바 표시, 확인 흐름, 결과 피드백.
- 의존: M1~M4.

## 3. 의존성 그래프

```
M1 (API/서비스) ──┬──> M2 (감사 로그)
                  └──> M4 (프론트 연동) ──> M5 (테스트)
M3 (프론트 UI) ───────> M4
```

M1과 M3은 병렬 착수 가능. M4는 M1·M3 완료 후. M5는 전체 완료 후.

## 4. 리스크

| 리스크 | 영향 | 완화 |
|--------|------|------|
| 건별 트랜잭션 격리 구현 복잡성 | 부분 실패 시 데이터 일관성 | `REQUIRES_NEW` 전파 또는 ID별 명시적 저장·예외 격리. 통합 테스트로 검증. |
| 대량(100건) 처리 시 N+1 / 성능 | 응답 지연 | 100건 상한으로 범위 제한. 필요 시 배치 조회. |
| `LOCKED→ACTIVE` unlock 의미 누락 | 잠금 카운트 미초기화로 즉시 재잠금 | 기존 `unlock` 로직 재사용으로 일관성 확보. AC-UBS-004-3로 검증. |
| 감사 로그 건별 기록 누락 | 추적성 저하(TRUST Trackable) | 성공 건마다 기록 단언 테스트(AC-UBS-008-1/2). |
| 프론트 cross-page 선택 오해 | 사용자가 다른 페이지 선택 유실 인지 못함 | 현재 페이지 선택만 지원 명시(비범위), 페이지 이동 시 선택 초기화 UX. |

## 5. 품질 게이트 (TRUST 5)

- **Tested**: 백엔드 단위·통합 테스트(부분 실패·권한·감사), 프론트 컴포넌트 테스트. 핵심 분기 커버.
- **Readable**: 기존 `UserController`/`UserListView` 명명·스타일 준수.
- **Unified**: 기존 `usersApi` 패턴, `@PreAuthorize` 패턴, Element Plus 컴포넌트 일관 사용.
- **Secured**: USER:WRITE 권한 검사, DELETED의 SUPER_ADMIN 제한, 100건 상한, 입력 검증(@NotEmpty/@Size).
- **Trackable**: 변경 성공 건별 audit_log, SPEC-ID 참조 커밋.
