# SPEC-CMS-USER-APPROVAL-001 — 진행 현황 (Progress)

- SPEC: 사용자 가입 승인/거절 관리
- 상태: Completed
- 최종 갱신: 2026-06-17

## 작업 진행 상태 (Task Tracking)

상태 범례: TODO / IN_PROGRESS / DONE / BLOCKED

| ID | 작업 | 상태 | 비고 |
|----|------|------|------|
| T0 | DB 마이그레이션 `V59__user_registration_approval.sql` (status 제약 재정의 + additive 3컬럼 + system_setting/권한/템플릿 시드) | DONE | 단일 파일 통합 (NFR-UA-C2) |
| T1 | `UserStatus.PENDING_APPROVAL` enum 추가 | DONE | |
| T2 | 가입 게이트 적용 (`AuthServiceImpl.registerPublicUser`) | DONE | `RegisterResult` sealed 타입으로 게이트 ON/OFF 분기 |
| T3 | 로그인 차단 (`AuthServiceImpl.login`, PENDING_APPROVAL 거부) | DONE | REQ-UA-004 |
| T4 | 승인 서비스 (`UserApprovalService`/`*Impl`, 단건·일괄) | DONE | 상태 검증 409, MEMBER 역할 보장 |
| T5 | 이메일 연동 (`EmailTemplateResolver`, after-commit, graceful fallback) | DONE | REQ-UA-017~019 |
| T6 | 컨트롤러/DTO (`UserApprovalController`, `@PreAuthorize`) | DONE | 6 엔드포인트, `UserApprovalMapper.xml` |
| T7 | 프론트 API 클라이언트 (`api/userApprovals.ts`) | DONE | |
| T8 | 프론트 대기열 화면 (`views/users/ApprovalQueueView.vue`) | DONE | 라우트 `/users/approvals`, 설정 토글 |
| T9 | 수용 기준 문서 (`acceptance.md`, Given-When-Then) | DONE | REQ-UA-001~021 전체 커버 |
| T10 | 테스트 (백엔드 단위·통합, 프론트 컴포넌트) | DONE | `MigrationOrderIT` 카운트 58→59 갱신 포함 |

## 요구사항 커버리지 (Requirement Coverage)

| 요구사항 | 담당 작업 | 상태 |
|----------|-----------|------|
| REQ-UA-001~003 (가입 게이트) | T0, T1, T2 | DONE |
| REQ-UA-004 (로그인 차단) | T3 | DONE |
| REQ-UA-005~006 (설정 관리) | T0 (시드) | DONE |
| REQ-UA-007~009 (대기열 조회) | T4, T6 | DONE |
| REQ-UA-010~013 (단건 승인/거절) | T4, T5, T6 | DONE |
| REQ-UA-014~016 (일괄 처리) | T4, T6 | DONE |
| REQ-UA-017~019 (이메일 알림) | T0 (템플릿), T5 | DONE |
| REQ-UA-020~021 (권한/감사) | T0 (권한 시드), T4, T6 | DONE |

## 품질 게이트 (Quality Gates)

| 게이트 | 상태 | 비고 |
|--------|------|------|
| 마이그레이션 V59 적용 (제약 재정의 무손실) | DONE | `MigrationOrderIT` EXPECTED_MIGRATION_COUNT 59로 검증 |
| 게이트 기본값 false 회귀 확인 | DONE | NFR-UA-C1, system_setting 시드 `'false'` |
| 이메일 발송 실패 graceful (상태 커밋 유지) | DONE | REQ-UA-019 |
| RBAC 403 검증 | DONE | NFR-UA-S1, `@PreAuthorize` 적용 |
| 테스트 커버리지 85%+ | DONE | TRUST 5 Tested |

## 변경 이력 (Change Log)

- 2026-06-17: 구현 완료. 모든 작업 DONE, 상태 Completed로 갱신.
- 2026-06-16: SPEC 작성, 모든 작업 TODO 상태로 초기화.

## 리스크 / 결정 대기 (Open Risks)

- **R1 (상태 제약 마이그레이션)**: `chk_users_status` DROP/ADD 시 운영 DB의 동시 쓰기 충돌 가능성. 배포 시 짧은 락 발생 — 점검 시간대 적용 권장. (구현 완료, 운영 배포 시 주의)
- **R2 (DEPT_ADMIN 데이터 범위)**: `PermissionScopeService`(SPEC-CMS-RBAC-001)에 따라 DEPT_ADMIN이 전체 대기열을 볼지 조직 범위만 볼지 현재 전체 대기열 조회로 구현됨. 범위 제한이 필요한 경우 후속 SPEC에서 처리.
- **R3 (이메일 발송 타이밍)**: after-commit 방식으로 구현 확정. 트랜잭션 커밋 후 비동기 발송.
