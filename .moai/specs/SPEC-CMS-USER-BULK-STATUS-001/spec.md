---
id: SPEC-CMS-USER-BULK-STATUS-001
version: 0.4.0
status: Completed
created: 2026-06-01
updated: 2026-06-09
author: manager-spec
priority: P1
parent: SPEC-CMS-002
related:
  - SPEC-CMS-002 (RBAC — USER:WRITE 권한, UserStatus 출처)
  - SPEC-CMS-005 (audit_log — 일괄 상태변경 감사 기록)
issue_number: TBD
---

# SPEC-CMS-USER-BULK-STATUS-001 — 사용자 일괄 상태 변경

## HISTORY

- 2026-06-09 (v0.3.0): Tested. CI GREEN (origin/main 6dc5e24) — 백엔드 Docker BUILD PASS, 프론트엔드 2/2 GREEN. CHANGELOG v2.4.0 동기화 완료.
- 2026-06-01 (v0.2.0): Implemented. 백엔드 Docker BUILD PASS (6 Mockito 테스트 GREEN), 프론트엔드 단위 테스트 2/2 GREEN. JwtPrincipal.roles() Set 기반으로 actorRole 도출. 기존 AdminNotificationServiceTest·DashboardLayoutServiceTest 컴파일 오류 사전 존재 확인됨.
- 2026-06-01 (v0.1.0): Draft 작성. 관리자가 사용자 목록에서 다수를 선택하여 계정 상태를 일괄 변경하는 기능 정의. 기존 개별 사용자 CRUD(SPEC-CMS-002) 및 감사 로그(SPEC-CMS-005) 인프라 재사용 기반.

---

## 1. 개요

### 1.1 목적

관리자가 사용자 목록(`UserListView`)에서 체크박스로 여러 사용자를 선택한 뒤, 단일 조작으로 여러 계정의 상태(`ACTIVE` / `INACTIVE` / `LOCKED`)를 한 번에 변경할 수 있도록 한다. 현재는 사용자별로 개별 수정 화면을 열어 상태를 바꿔야 하므로, 다수 계정의 일괄 비활성화·재활성화 작업에 반복 조작이 필요하다.

### 1.2 배경

- 기존 `UserController`는 개별 사용자 단위 작업만 제공한다 (`PUT /{id}`, `POST /{id}/unlock` 등).
- 운영 상황(예: 부서 통폐합, 휴면 계정 일괄 비활성화, 보안 사고 대응 후 일괄 재활성화)에서 다수 계정을 한 번에 처리할 필요가 있다.
- 일괄 변경은 개별 변경과 동일한 권한·감사 정책을 따라야 하며, 부분 실패가 발생해도 성공 가능한 항목은 처리되어야 한다.

### 1.3 범위

본 SPEC은 다음을 정의한다.

- 백엔드: `PATCH /api/v1/users/bulk-status` 신규 엔드포인트 및 `UserService.bulkUpdateStatus(...)` 신규 메서드.
- 프론트엔드: `UserListView.vue`의 체크박스 다중선택, 일괄 작업 툴바, 확인 다이얼로그, 결과 피드백.
- 감사: 변경 성공 건별 `audit_log` 기록.

---

## 2. 기존 인프라 (재사용)

| 항목 | 위치 | 재사용 방식 |
|------|------|-------------|
| `UserController` | `backend/.../auth/controller/UserController.java` | `@PatchMapping("/bulk-status")` 메서드만 추가. 기존 `@RequestMapping("/api/v1/users")` 및 `@Validated` 그대로 사용. |
| `UserService` / `UserServiceImpl` | `backend/.../auth/service/` | `bulkUpdateStatus(...)` 신규 메서드 추가. 기존 개별 `update`/`unlock` 로직 재사용 가능. |
| `UserStatus` enum | `backend/.../auth/entity/UserStatus.java` | `ACTIVE`/`INACTIVE`/`LOCKED`/`DELETED` 값 그대로 사용 (신규 값 추가 없음). |
| `JwtPrincipal` | `backend/.../auth/security/JwtPrincipal.java` | `@AuthenticationPrincipal`로 호출자 식별 (`principal.userId()`). |
| RBAC `@PreAuthorize` | Spring Security 6 | 기존 `hasRole`/`hasAnyRole` 패턴 재사용. |
| `AuditLogAspect` / 감사 기록 | SPEC-CMS-005 | 변경 건별 `audit_log` 기록에 재사용. |
| `usersApi` | `frontend/admin/src/api/users.ts` | `bulkUpdateStatus()` 함수 추가. 기존 `apiClient` 패턴 재사용. |
| `UserListView.vue` | `frontend/admin/src/views/users/` | `el-table` selection 컬럼·툴바 추가. 기존 `loadUsers()`·`ElMessageBox`·i18n 재사용. |
| Element Plus | 프론트엔드 UI | `el-table` `type="selection"`, `el-button`, `ElMessageBox`, `ElMessage` 재사용. |

---

## 3. 신규 도입 항목 (Gap)

| Gap | 신규 항목 | 비고 |
|-----|-----------|------|
| 일괄 상태변경 API 부재 | `PATCH /api/v1/users/bulk-status` | `{ userIds: number[], targetStatus: string }` 수신, 부분 실패 결과 반환. |
| 서비스 일괄 처리 로직 부재 | `UserService.bulkUpdateStatus(List<Long>, UserStatus, JwtPrincipal)` | 상태 전환 규칙·권한·감사 처리 포함. |
| 요청/응답 DTO 부재 | `BulkStatusUpdateRequest`, `BulkStatusUpdateResponse` | 성공 ID 목록 + 실패 항목(ID, 사유) 목록. |
| 다중선택 UI 부재 | `el-table` selection 컬럼 | 체크박스 열 + 선택 상태 관리. |
| 일괄 작업 툴바 부재 | bulk action toolbar 컴포넌트/영역 | 선택 카운터 + 대상 상태 선택 + 실행 버튼. |
| 프론트 API 함수 부재 | `usersApi.bulkUpdateStatus()` | 신규 엔드포인트 래퍼. |

---

## 4. 범위 및 비범위

### 4.1 범위 (In Scope)

- 체크박스 기반 다중선택 및 선택 상태 관리.
- `ACTIVE` / `INACTIVE` / `LOCKED` 상태로의 일괄 전환.
- `LOCKED → ACTIVE` 전환을 잠금 해제와 동일 의미로 처리.
- 부분 실패 허용 및 실패 항목 사유 반환.
- 권한 검사(USER:WRITE) 및 `DELETED` 상태 변경의 SUPER_ADMIN 제한.
- 변경 건별 감사 로그 기록.
- 최대 선택 건수 제한(100건) 및 결과 피드백.

### 4.2 Exclusions (What NOT to Build)

- 일괄 **삭제**(soft delete) — 본 SPEC은 상태 변경만 다루며, 일괄 삭제는 별도 SPEC으로 분리한다.
- 일괄 비밀번호 초기화 / 일괄 강제 로그아웃 — 본 SPEC 범위 아님.
- 일괄 부서(organization) 이동 또는 역할(role) 변경 — 상태 외 속성 변경은 제외.
- 변경 작업 예약/스케줄링 — 즉시 실행만 지원.
- 변경 결과 비동기 처리(큐/배치 잡) — 동기 트랜잭션 처리만 지원.
- 변경 작업 되돌리기(undo) 기능 — 제외.
- 페이지를 넘나드는 누적 선택(cross-page selection) — 현재 페이지 내 선택만 지원.

---

## 5. 신규 요구사항 (EARS)

### REQ-UBS-001 — 체크박스 다중선택 (Optional/Ubiquitous)
**Where** 사용자 목록 화면(`UserListView`)이 표시되는 경우, the 시스템 **shall** 각 사용자 행 좌측에 선택 체크박스 열을 제공하고, 헤더 체크박스로 현재 페이지 전체 선택/해제를 지원한다.

### REQ-UBS-002 — 선택 카운터 및 일괄 작업 툴바 (Event-Driven)
**When** 사용자가 1명 이상을 선택하면, the 시스템 **shall** 선택된 사용자 수와 대상 상태 선택 컨트롤, 실행 버튼을 포함하는 일괄 작업 툴바를 표시한다. 선택이 0건이 되면 툴바를 숨긴다.

### REQ-UBS-003 — 일괄 상태변경 API (Ubiquitous)
The 시스템 **shall** `PATCH /api/v1/users/bulk-status` 엔드포인트를 제공하며, 요청 본문으로 `{ userIds: number[], targetStatus: string }`를 받아 각 사용자 상태를 `targetStatus`로 변경하고 처리 결과(성공 ID 목록, 실패 항목 목록)를 반환한다.

### REQ-UBS-004 — 상태 전환 허용 규칙 (State-Driven)
**While** 일괄 상태변경을 처리하는 동안, the 시스템 **shall** 다음 규칙을 적용한다: `targetStatus`는 `ACTIVE`/`INACTIVE`/`LOCKED`만 허용한다(요청 단위); 선택된 사용자 중 현재 상태가 `DELETED`인 항목은 변경하지 않고 실패 항목으로 분류한다; `LOCKED` 상태 사용자를 `ACTIVE`로 전환하는 경우 잠금 해제(unlock)와 동일하게 처리한다(로그인 실패 카운트 초기화 포함).

### REQ-UBS-005 — 확인 다이얼로그 (Event-Driven)
**When** 사용자가 일괄 작업 실행 버튼을 누르면, the 시스템 **shall** 영향받는 사용자 수와 대상 상태를 명시한 확인 다이얼로그를 표시하고, 사용자가 확인한 경우에만 API를 호출한다.

### REQ-UBS-006 — 부분 실패 허용 (Unwanted Behavior)
**If** 일부 사용자 ID에 대한 변경이 실패하면(존재하지 않음·`DELETED` 상태·권한 부족 등), **then** the 시스템 **shall** 실패한 항목을 건너뛰고 나머지 항목 처리를 계속하며, 실패 항목별 ID와 사유를 응답에 포함한다.

### REQ-UBS-007 — 권한 검사 (State-Driven)
**While** 일괄 상태변경 요청을 처리하는 동안, the 시스템 **shall** 호출자가 `USER:WRITE` 권한을 보유한 경우에만 처리하고, 보유하지 않으면 403을 반환한다. 또한 `targetStatus`가 `DELETED`인 요청(향후 확장 대비)은 `SUPER_ADMIN` 역할만 허용한다.

### REQ-UBS-008 — 감사 로그 (Event-Driven)
**When** 일괄 상태변경으로 사용자 상태가 실제로 변경되면, the 시스템 **shall** 변경된 사용자별로 `audit_log`에 1건씩 기록하며(`action='UPDATE'`, `entity_type='USER'`, before/after 상태 포함), 실패 항목은 기록하지 않는다.

### REQ-UBS-009 — 최대 선택 제한 (Unwanted Behavior)
**If** 사용자가 100건을 초과하여 선택을 시도하면, **then** the 시스템 **shall** 경고 메시지를 표시하고 초과분의 선택을 허용하지 않으며, 서버는 `userIds`가 100건을 초과한 요청을 400으로 거부한다.

### REQ-UBS-010 — 결과 피드백 (Event-Driven)
**When** 일괄 상태변경 API 응답을 수신하면, the 시스템 **shall** 성공 건수와 실패 건수를 토스트로 표시하고, 실패가 있으면 상세 보기 수단을 제공하며, 사용자 목록을 자동 갱신하고 선택 상태를 초기화한다.

### REQ-UBS-011 — 빈 입력 거부 (Unwanted Behavior)
**If** 요청의 `userIds`가 비어 있거나 누락되면, **then** the 시스템 **shall** 400 Bad Request를 반환하고 상태를 변경하지 않는다.

### REQ-UBS-012 — 트랜잭션 무결성 (Ubiquitous)
The 시스템 **shall** 각 사용자별 상태 변경을 독립적으로 처리하여, 한 사용자의 실패가 다른 사용자의 성공 커밋을 롤백시키지 않도록 한다(건별 격리).

---

## 6. 수락 기준 (Acceptance Criteria)

상세 Given-When-Then 시나리오는 `acceptance.md` 참조. 요약:

### REQ-UBS-001 / 002 (다중선택·툴바)
- **AC-UBS-001-1**: 목록 각 행에 체크박스가 렌더링되고, 헤더 체크박스 클릭 시 현재 페이지 전체가 선택된다.
- **AC-UBS-002-1**: 0건 선택 시 툴바가 보이지 않고, 1건 이상 선택 시 툴바가 나타나며 "N명 선택됨" 카운터를 표시한다.

### REQ-UBS-003 (API)
- **AC-UBS-003-1**: 빈 `userIds` → 400 Bad Request.
- **AC-UBS-003-2**: 존재하지 않는 `userId` 포함 → 해당 ID는 실패 목록에 기록되고, 나머지 유효 ID는 정상 처리된다.
- **AC-UBS-003-3**: 유효 ID 3건·`targetStatus=INACTIVE` → 응답에 성공 ID 3건, 각 사용자 상태가 `INACTIVE`로 변경된다.

### REQ-UBS-004 (전환 규칙)
- **AC-UBS-004-1**: `DELETED` 상태 사용자가 선택에 포함 → 해당 ID 실패, 사유="DELETED 상태는 일괄 변경 불가".
- **AC-UBS-004-2**: SUPER_ADMIN 외 역할이 `targetStatus=DELETED`로 변경 시도 → 403.
- **AC-UBS-004-3**: `LOCKED` 사용자를 `ACTIVE`로 일괄 변경 → 상태가 `ACTIVE`로 바뀌고 로그인 실패 카운트가 초기화된다(unlock 동등 처리).
- **AC-UBS-004-4**: `targetStatus`가 허용되지 않는 값 → 400.

### REQ-UBS-005 (확인 다이얼로그)
- **AC-UBS-005-1**: 3명 선택 후 실행 → 확인 다이얼로그에 "3명의 계정 상태를 INACTIVE로 변경합니다" 표시.
- **AC-UBS-005-2**: 확인 다이얼로그에서 취소 → API 미호출, 선택 상태 유지.

### REQ-UBS-006 (부분 실패)
- **AC-UBS-006-1**: 5건 중 2건 실패 → 응답에 성공 ID 3건 + 실패 항목 2건(ID·사유), 성공 3건은 실제 상태가 변경된다.

### REQ-UBS-007 (권한)
- **AC-UBS-007-1**: `USER:WRITE` 권한 없는 토큰 → 403.
- **AC-UBS-007-2**: DEPT_ADMIN이 `USER:WRITE` 보유 시 일반 상태(ACTIVE/INACTIVE/LOCKED) 일괄 변경 허용.

### REQ-UBS-008 (감사 로그)
- **AC-UBS-008-1**: 30건 일괄 변경 성공 → `audit_log` 30건 INSERT (`action=UPDATE`, `entity_type=USER`).
- **AC-UBS-008-2**: 5건 중 2건 실패 → `audit_log`는 성공 3건만 INSERT.

### REQ-UBS-009 (최대 선택)
- **AC-UBS-009-1**: 101건 선택 시도 → "최대 100건만 선택 가능합니다" 경고, 초과분 선택 해제.
- **AC-UBS-009-2**: 서버에 101건 `userIds` 전송 → 400.

### REQ-UBS-010 (결과 피드백)
- **AC-UBS-010-1**: 30건 성공·0건 실패 → 토스트 "30명의 상태가 변경되었습니다", 목록 자동 갱신, 선택 초기화.
- **AC-UBS-010-2**: 28건 성공·2건 실패 → 토스트 "28명 성공, 2명 실패 (상세 보기)".

### REQ-UBS-011 / 012 (빈 입력·트랜잭션)
- **AC-UBS-011-1**: `userIds` 누락 요청 → 400, DB 변경 없음.
- **AC-UBS-012-1**: 일부 ID 처리 중 예외 발생 → 해당 ID만 실패 처리되고, 이미 성공한 변경은 커밋 상태로 유지된다.

총 AC: 22개.

---

## 7. 기술 접근법

### 7.1 백엔드

- **컨트롤러**: `UserController`에 신규 메서드 추가.
  - `@PatchMapping("/bulk-status")`
  - `@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")` (USER:WRITE 매핑 역할). `DELETED` 대상은 서비스 계층에서 SUPER_ADMIN 추가 검증.
  - 시그니처: `BulkStatusUpdateResponse bulkUpdateStatus(@Valid @RequestBody BulkStatusUpdateRequest req, @AuthenticationPrincipal JwtPrincipal principal)`
- **DTO**:
  - `BulkStatusUpdateRequest`: `@NotEmpty @Size(max=100) List<Long> userIds`, `@NotNull UserStatus targetStatus`. `targetStatus`는 `ACTIVE`/`INACTIVE`/`LOCKED`만 허용(별도 검증).
  - `BulkStatusUpdateResponse`: `List<Long> succeeded`, `List<FailedItem> failed` (FailedItem = `{ Long userId, String reason }`).
- **서비스**: `UserService.bulkUpdateStatus(List<Long> ids, UserStatus target, JwtPrincipal principal)` 신규.
  - 각 ID를 순회하며 건별로 처리(건별 트랜잭션 격리 — `REQUIRES_NEW` 또는 ID별 try/catch + 개별 save).
  - 상태 전환 규칙 적용(REQ-UBS-004): `DELETED` 항목 스킵, `LOCKED→ACTIVE`는 unlock 로직 호출.
  - `DELETED` 대상 요청 시 SUPER_ADMIN 권한 추가 검증(403).
  - 성공 건별 감사 로그 기록(before/after status 포함).
- **감사 로그**: 건별 변경이므로 메서드 단위 `@Audit` 어노테이션보다 `AuditLogAspect` 수동 호출 또는 서비스 내 명시적 기록이 적합. SPEC-CMS-005 패턴에 맞춰 구현.

### 7.2 프론트엔드

- **`UserListView.vue`**:
  - `el-table`에 `@selection-change="onSelectionChange"` 바인딩 및 `<el-table-column type="selection" width="48" :selectable="isSelectable" />` 추가.
  - `isSelectable(row)`로 `DELETED` 상태 행을 선택 불가 처리(UX 가드, 서버 검증과 이중 방어).
  - `selectedRows` ref로 선택 상태 관리, 카운터·툴바 표시 조건(`selectedRows.length > 0`).
  - 100건 초과 시 경고(`ElMessage.warning`) 및 초과 선택 해제(REQ-UBS-009).
  - 실행 시 `ElMessageBox.confirm`으로 확인(REQ-UBS-005), 확인 시 `usersApi.bulkUpdateStatus()` 호출.
  - 응답 후 `ElMessage`로 결과 토스트(REQ-UBS-010), `loadUsers()` 재호출, 선택 초기화.
- **`usersApi`**:
  - `bulkUpdateStatus(userIds: number[], targetStatus: UserStatus)` → `apiClient.patch('/users/bulk-status', { userIds, targetStatus })`.
- **i18n**: `users.bulk.*` 키 추가(선택 카운터, 확인 메시지, 성공/실패 토스트, 경고 메시지).

---

## 8. 구현 파일 목록

### 신규 (Backend)
- `backend/.../auth/dto/BulkStatusUpdateRequest.java`
- `backend/.../auth/dto/BulkStatusUpdateResponse.java`

### 수정 (Backend)
- `backend/.../auth/controller/UserController.java` — `bulkUpdateStatus` 메서드 추가
- `backend/.../auth/service/UserService.java` — 인터페이스에 `bulkUpdateStatus` 선언
- `backend/.../auth/service/UserServiceImpl.java` — 구현

### 수정 (Frontend)
- `frontend/admin/src/views/users/UserListView.vue` — selection 컬럼·툴바·핸들러
- `frontend/admin/src/api/users.ts` — `bulkUpdateStatus` 함수
- `frontend/admin/src/i18n/` (해당 locale 파일) — `users.bulk.*` 키

### 테스트
- Backend: `UserControllerTest` / `UserServiceImplTest`에 일괄 변경·부분 실패·권한·감사 케이스 추가
- Frontend: `UserListView` 컴포넌트 테스트(선택·툴바·확인·피드백)
