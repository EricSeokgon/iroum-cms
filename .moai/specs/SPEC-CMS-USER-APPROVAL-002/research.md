# SPEC-CMS-USER-APPROVAL-002 — 리서치 (사용자 가입 승인 흐름 고도화)

작성: 2026-06-25 / manager-spec
대상 기능: ① 이메일 인증 코드 송신 ② 승인 대기 리마인더/자동 만료 스케줄러 ③ 일괄 승인/거절 + 사유

---

## 0. [중요] 브랜치/머지 상태 (선결 컨텍스트)

본 SPEC이 고도화하는 **SPEC-CMS-USER-APPROVAL-001은 `main`에 머지되지 않은 상태**다.

| 사실 | 근거 |
|------|------|
| `feat/SPEC-CMS-USER-APPROVAL-002` 는 `main`(c4c3084, KPI-002)에서 분기 | 본 작업에서 생성 |
| APPROVAL-001 구현은 `feat/SPEC-CMS-USER-APPROVAL-001` 브랜치에만 존재 (main 대비 11 커밋 미머지) | `git rev-list --count main..feat/SPEC-CMS-USER-APPROVAL-001` = 11 |
| `main`에는 `domain/approval` 패키지, `V58` 마이그레이션, `PENDING_APPROVAL` enum 모두 **없음** | `ls domain/approval` = 부재, main 최신 마이그레이션 = **V53** |
| `EMAIL-TEMPLATE-001`(email_template, V55), `NOTI-EXT-001` 도 동일하게 미머지 | git log 상 커밋만 존재, main SPEC 디렉터리 부재 |

[HARD] 결과: **SPEC-002의 run 단계는 APPROVAL-001(및 EMAIL-TEMPLATE-001)이 main에 머지된 이후에만 시작 가능**하다. SPEC-002는 APPROVAL-001을 **선행 의존성**으로 명시하며, 본 문서의 "기구현(재사용)" 항목은 APPROVAL-001 브랜치 기준 실측이다.

---

## 1. 기존 가입 승인 흐름 (현재 상태)

APPROVAL-001(`feat/SPEC-CMS-USER-APPROVAL-001`, Completed)이 게이트형 가입 승인 워크플로를 완성했다.

### 1.1 게이트 메커니즘
- 설정 `system_setting`(V14) 키 `REGISTRATION_APPROVAL_REQUIRED`(BOOL, 기본 `false`).
- `AuthServiceImpl.registerPublicUser`(현재 main 508행 `status(UserStatus.ACTIVE)` 하드코딩)에 게이트 분기 주입.
- 게이트 ON → `UserStatus.PENDING_APPROVAL` 생성, JWT 미발급. 게이트 OFF → 기존 즉시 활성.
- `PENDING_APPROVAL` 사용자는 로그인 거부(REQ-UA-004, `UserPendingApprovalException`).

### 1.2 승인/거절 도메인 (`kr.co.ircp.cms.domain.approval`)
- `UserApprovalController` — base path `/api/v1/users/approvals`, 6 엔드포인트:
  - `GET /` (대기열 목록, 검색/페이지), `GET /{id}` (상세)
  - `POST /{id}/approve`, `POST /{id}/reject`(사유 필수)
  - `POST /bulk-approve`, `POST /bulk-reject`(공통 사유)
- `UserApprovalServiceImpl` — `updateApprovalStatus` 행수 0 → `UserNotPendingApprovalException`(409). 일괄은 self-proxy + 건별 독립 트랜잭션, 이메일은 `afterCommit` 콜백 발송(REQ-UA-019 graceful).
- `UserApprovalMapper.xml`, `UserApprovalSummary`, `BulkOperationResult`.
- 인가: `@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")`.

### 1.3 데이터 (V58, APPROVAL-001)
- `users` additive 3컬럼: `approval_status_changed_at TIMESTAMPTZ`, `approval_changed_by BIGINT FK users(id)`, `rejection_reason TEXT`. 모두 nullable.
- `chk_users_status` 제약 DROP+ADD 로 `PENDING_APPROVAL` 추가.
- 권한 시드 `USER_APPROVAL:READ|APPROVE|REJECT`(action READ/WRITE, V6 permissions CHECK 준수) + SUPER_ADMIN 매핑.
- 이메일 템플릿 시드 `USER_APPROVAL_CONFIRMED`/`USER_APPROVAL_REJECTED`(ko/en, Thymeleaf `${userName}`/`${rejectionReason}`).

---

## 2. 이메일/인증 인프라 (기구현)

### 2.1 본인인증 OTP (SPEC-CMS-002, **main에 존재**)
[HARD] 신규 인증 코드 인프라를 만들 필요 없음 — 이미 완성된 OTP 시스템이 있다.

- `VerificationService`(`domain.auth.service`): `request()` / `confirm()` / `validateVerifiedToken()`.
- `VerificationPurpose` enum에 **`SIGNUP` 이미 존재**(회원가입 시 이메일 확인 목적). `PASSWORD_RESET`, `IMPORTANT_CHANGE` 도 존재.
- `VerificationChannel`, `VerificationStatus`, `VerificationRequest`/`VerificationHistory` 엔티티 + 매퍼.
- 예외: `VerificationExpiredException`, `VerificationCodeMismatchException`, `VerificationCooldownException`, `VerificationAttemptExceededException`, `VerificationIpBlockedException`, `InvalidVerifiedTokenException`.
- 마이그레이션: **`V8__verification_schema.sql`** (main 존재). 만료/쿨다운/시도횟수/IP차단 기반 보안 OTP.
- DTO: `VerifyRequestRequest/Response`, `VerifyConfirmRequest/Response` — `verifiedToken`(5분 유효) 발급.

→ Area 1(이메일 인증 코드)은 **이 OTP를 `SIGNUP` 목적으로 가입 흐름에 연결**하는 작업이지, 신규 구축이 아니다. `confirm()` 이 발급한 `verifiedToken`을 register 요청이 검증(`validateVerifiedToken(token, SIGNUP)`)하는 패턴은 이미 `confirmPasswordReset`(AuthServiceImpl 428행)에서 검증됨.

### 2.2 이메일 발송 (SPEC-CMS-EMAIL-TEMPLATE-001, APPROVAL-001 브랜치)
- `EmailTemplateResolver.resolveAndRender(code, lang, vars)` → Optional, 실패 비전파(graceful).
- `email_template` 테이블(V55), 시드 패턴 `ON CONFLICT (code, language) DO NOTHING`, Thymeleaf `${var}`.
- `EmailService` — 실발송. APPROVAL-001의 `UserApprovalServiceImpl`이 `afterCommit`에서 호출.

---

## 3. 스케줄러/배치 인프라 (기구현)

[HARD] Spring Batch 미사용. **평이한 `@Scheduled` 컴포넌트 패턴**이 표준이며 다수 존재한다.

- `@EnableScheduling` 위치: `kr.co.ircp.cms.config.AsyncConfig`.
- 대표 패턴 (`domain.board.service.QnaNotificationRetryJob`): `@Component` + `@RequiredArgsConstructor` + `@Scheduled(cron=...)` 메서드가 서비스 레이어 1줄 위임. 백오프/필터링은 서비스에서 DB 컬럼(retry_count 등)으로 판단.
- 기타 스케줄 잡: `domain.governance.batch.*`(통계/보존/아카이브 10+종), `board.service.PostPublishJob`/`PublicationZipExpireJob`, `dashboard.kpi.job.KpiAggregationJob`, `ai.job.AiModelMetricJob`.
- `@Async` 도 `AsyncConfig`에서 활성(이메일 비동기 발송 가능).

→ Area 2(리마인더/자동 만료)는 **신규 `@Scheduled` 잡 1~2개 + 대기열 쿼리 + 메타 컬럼**으로 구현. 임계값(N일, max_wait_days)은 `system_setting`(V14) key-value로 저장(신규 config 테이블 금지).

---

## 4. 프론트엔드 (기구현)

`frontend/admin/` (Vue 3 + Element Plus + pnpm), APPROVAL-001 브랜치:
- `views/users/ApprovalQueueView.vue` — **이미 일괄 UI 완비**:
  - `el-table type="selection"` + `@selection-change`, `bulk-approve-btn`/`bulk-reject-btn`.
  - 거절 사유 다이얼로그(`reject-reason-input`, 필수 검증 `:disabled="!rejectReason.trim()"`), 단건/일괄 공용(`rejectTarget` null=일괄).
- `api/userApprovals.ts` — `approve`/`reject(reason)`/`bulkApprove(userIds)`/`bulkReject(userIds, reason)` + `BulkOperationResult`/`PendingUser` 타입.
- 테스트 `tests/views/users/ApprovalQueueView.spec.ts`.

→ Area 3(일괄 승인/거절 + 사유)는 **백엔드·프론트 모두 사실상 완성**. SPEC-002의 Area 3은 **순신규가 아니라 경량 보강**(예: 거절 결과 부분실패 상세 표시, 인증 미완료 사용자 구분 뱃지)으로만 의미가 있다.

---

## 5. DB 스키마 핵심 (users / 관련 테이블)

- `users`(V2): id/uuid/username/email(암호화)/password_hash/name/status/fail_count/locked_until/organization_id/created_at/updated_at/deleted_at. 부분 인덱스 `idx_users_status`(WHERE deleted_at IS NULL).
- `users` additive(V58, APPROVAL-001): approval_status_changed_at, approval_changed_by, rejection_reason.
- `system_setting`(V14): key/value/value_type(CHECK STRING|INT|BOOL|JSON)/description. 설정은 전부 여기.
- `email_template`(V55): code/name/template_type(CHECK, CUSTOM 사용)/language/subject/body_html/body_text/variables(jsonb)/is_active/created_by. PK(code, language).
- `verification_*`(V8): OTP 요청/이력.
- `permissions`(V6, action CHECK READ|WRITE|DELETE|EXECUTE|ADMIN) / `role_permissions`(role_code, permission_code).

### 마이그레이션 번호
- main 최신 = **V53**. APPROVAL-001 = V58. (V54/V55/V56/V57은 EMAIL-TEMPLATE/POINTS 등 미머지 브랜치가 잠정 사용 중 — 충돌 위험.)
- [HARD] SPEC-002 run 직전, **APPROVAL-001 머지 후의 실제 최신 마이그레이션 번호를 재확인**하고 그 다음 번호 사용. 본 SPEC 문서에서는 잠정 **V65**로 표기(APPROVAL-001 V58 다음 가정).

---

## 6. 채워야 할 갭 (Gap Analysis)

| 영역 | 기구현(재사용) | 진짜 갭(SPEC-002) | 신규성 |
|------|----------------|-------------------|--------|
| ① 이메일 인증 코드 | VerificationService(OTP)+SIGNUP purpose, V8, verifiedToken 패턴, EmailTemplate | register 흐름에 SIGNUP OTP 검증 연동, 미인증 시 가입/포털 접근 차단(403), 재발송 연동, 코드 만료(기 OTP 만료 재사용) | **연동 위주** |
| ② 리마인더/자동만료 | `@Scheduled` 잡 패턴, system_setting, EmailTemplate, afterCommit 발송 | 대기 N일 경과 리마인더 잡, max_wait_days 초과 자동 거절 잡, 대기 메타 컬럼(reminder_sent_at 등), 설정 키 2종, 리마인더/만료 이메일 템플릿 2종 | **순신규** |
| ③ 일괄 승인/거절+사유 | bulk-approve/bulk-reject API, 사유 다이얼로그, 일괄 UI 전부 | (경량) 부분실패 상세 표시, 인증완료 여부 컬럼 노출. **순신규 아님** | **기구현/보강** |

### 권고 (run 전 결정 필요)
- Area 3은 APPROVAL-001에 이미 있음 → SPEC-002에서는 **REQ로 명세하되 "기구현 검증 + 경량 보강"으로 범위 제한**. 중복 재구현 금지.
- Area 1은 신규 인증 시스템이 아니라 **기존 OTP 연동** — 가장 큰 결정은 "가입 전 인증(register 차단)" vs "가입 후 인증(PENDING_EMAIL 별도 상태)". 본 SPEC은 register 진입 시 `verifiedToken` 필수화(가입 전 인증)를 채택해 신규 상태 추가를 피한다.
- Area 2가 유일한 대형 순신규 작업 → SPEC-002의 무게중심.
