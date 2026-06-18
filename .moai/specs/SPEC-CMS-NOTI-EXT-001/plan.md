# Plan — SPEC-CMS-NOTI-EXT-001

알림 기능 확장: 이메일 채널 발송 / 알림 템플릿 관리 / 발송 스케줄러

---

## 구현 전략

- **개발 방법론**: TDD (RED-GREEN-REFACTOR)
- **하네스 수준**: standard
- **우선순위**: High

---

## Phase 1 — 백엔드 기반 (V61 + 템플릿 CRUD)

### 1-1. V61 마이그레이션
- 파일: `backend/src/main/resources/db/migration/V61__notification_template_extension.sql`
- 내용: `notification_template` ALTER ADD COLUMN, `(code, language)` UNIQUE 전환, 권한 시드 4개, SUPER_ADMIN 매핑
- 사전 확인: 실제 DB에서 `notification_template_code_key` 제약명 확인 후 DROP

### 1-2. NotificationTemplate 엔티티 + Mapper + 서비스
- `NotificationTemplate.java` — 엔티티
- `NotificationTemplateMapper.java` + `NotificationTemplateMapper.xml` — MyBatis
- `NotificationTemplateService.java` / `NotificationTemplateServiceImpl.java`
  - create, update, delete (FK 체크), findById, findPage, preview
- 레퍼런스: `EmailTemplateAdminController` 패턴

### 1-3. NotificationTemplateAdminController
- 경로: `/api/v1/admin/notification-templates`
- `@PreAuthorize` per method: READ/WRITE/DELETE
- DTO: `NotificationTemplateCreateRequest`, `NotificationTemplateUpdateRequest`, `NotificationTemplateResponse`, `NotificationTemplatePreviewRequest`

### 1-4. PolicyDispatchController 권한 보강
- `PolicyDispatchController.java` — create/trigger/cancel에 `@PreAuthorize` 추가
- `DISPATCH:WRITE` 또는 `SUPER_ADMIN`

---

## Phase 2 — 발송 워커 (Dispatch Worker)

### 2-1. DispatchSchedulerConfig
- `DispatchSchedulerConfig.java` — `@EnableScheduling` + `dispatchExecutor` 빈 (`ThreadPoolTaskScheduler`)
- `auditExecutor`와 다른 이름으로 격리

### 2-2. DispatchChannelExecutor 인터페이스 + 구현체
- `DispatchChannelExecutor.java` — `interface dispatch(recipient, template, scheduleId)`
- `EmailDispatchExecutor.java` — `EmailService` 재사용, `EmailEncryptionService.decrypt()` 호출
- `InappDispatchExecutor.java` — `user_notification_inbox` INSERT (admin_notification 금지)

### 2-3. NotificationDispatchScheduleMapper 확장
- claim 쿼리: `UPDATE ... WHERE status='PENDING' AND scheduled_at <= now() ORDER BY priority DESC, scheduled_at ASC LIMIT 10`
- 상태 전이: PENDING → PROCESSING → COMPLETED / FAILED

### 2-4. NotificationDispatchTargetMapper 확장
- 멱등 INSERT: `INSERT ON CONFLICT (idempotency_key) DO NOTHING`
- 상태 업데이트: SENT / FAILED(error_message) / SKIPPED_OPTOUT

### 2-5. NotificationDispatchWorker
- `@Scheduled(fixedDelay=60_000)`
- claim → channel loop → target loop → 옵트아웃 체크 → 멱등 체크 → executor.dispatch → 결과 기록 → schedule 상태 전이

---

## Phase 3 — 프론트엔드

### 3-1. API 모듈
- `frontend/admin/src/api/notificationTemplate.ts`
- `getNotificationTemplates(params)`, `createNotificationTemplate(data)`, `updateNotificationTemplate(id, data)`, `deleteNotificationTemplate(id)`, `previewNotificationTemplate(id, variables)`

### 3-2. Pinia 스토어
- `frontend/admin/src/stores/notificationTemplate.ts`
- `templates`, `loading`, `fetchTemplates`, `createTemplate`, `deleteTemplate`

### 3-3. NotificationTemplateListView.vue
- `frontend/admin/src/views/notification/NotificationTemplateListView.vue`
- 목록 + 검색 필터(channel, isActive)
- 생성/수정/삭제/미리보기 액션 — 권한별 활성/비활성

### 3-4. 라우터 등록
- `frontend/admin/src/router/index.ts` 또는 notification 라우터 모듈에 경로 추가

### 3-5. PolicyDispatchView 드롭다운 연동
- `frontend/admin/src/views/policy/PolicyDispatchView.vue`
- 기존 하드코딩/빈 배열 → `getNotificationTemplates()` 실연동

---

## 파일 변경 목록

### 백엔드 신규 (14개)
| 파일 | 설명 |
|------|------|
| `V61__notification_template_extension.sql` | DB 마이그레이션 |
| `NotificationTemplate.java` | 엔티티 |
| `NotificationTemplateCreateRequest.java` | DTO (record) |
| `NotificationTemplateUpdateRequest.java` | DTO (record) |
| `NotificationTemplateResponse.java` | DTO (record) |
| `NotificationTemplatePreviewRequest.java` | DTO (record) |
| `NotificationTemplateMapper.java` | MyBatis Mapper |
| `NotificationTemplateMapper.xml` | SQL |
| `NotificationTemplateService.java` | 서비스 인터페이스 |
| `NotificationTemplateServiceImpl.java` | 서비스 구현체 |
| `NotificationTemplateAdminController.java` | REST 컨트롤러 |
| `DispatchChannelExecutor.java` | 채널 실행기 인터페이스 |
| `EmailDispatchExecutor.java` | EMAIL 채널 |
| `InappDispatchExecutor.java` | INAPP 채널 |
| `NotificationDispatchWorker.java` | @Scheduled 워커 |
| `DispatchSchedulerConfig.java` | 스레드풀 설정 |

### 백엔드 수정 (3개)
| 파일 | 변경 내용 |
|------|----------|
| `PolicyDispatchController.java` | @PreAuthorize 추가 |
| `NotificationDispatchScheduleMapper.{java,xml}` | claim/상태전이 쿼리 |
| `NotificationDispatchTargetMapper.{java,xml}` | 멱등 INSERT/상태 업데이트 |

### 프론트엔드 신규/수정 (5개)
| 파일 | 설명 |
|------|------|
| `api/notificationTemplate.ts` | API 모듈 (신규) |
| `stores/notificationTemplate.ts` | Pinia 스토어 (신규) |
| `views/notification/NotificationTemplateListView.vue` | 목록 뷰 (신규) |
| `router/...` | 라우트 등록 (수정) |
| `views/policy/PolicyDispatchView.vue` | 드롭다운 연동 (수정) |

**총 파일: 24개 (신규 21개, 수정 3개)**

---

## 테스트 계획 (TDD RED-GREEN-REFACTOR)

| 테스트 | 커버 AC |
|--------|---------|
| `NotificationTemplateServiceImplTest` | AC-NE-003, 004, 005, 006, 007 |
| `NotificationTemplateAdminControllerTest` | AC-NE-003 (403), 005 (409) |
| `NotificationDispatchWorkerTest` | AC-NE-008, 009, 011, 012, 013, 014, 015, 020 |
| `EmailDispatchExecutorTest` | AC-NE-010 (PII), AC-NE-006 (위임) |
| `InappDispatchExecutorTest` | AC-NE-009 (user_notification_inbox만) |
| `V61MigrationTest` | AC-NE-001, 002, 017 |
| `PolicyDispatchControllerSecurityTest` | AC-NE-016 |

목표 커버리지: 85%+

---

## 의존성 / 사전 확인

1. `notification_template_code_key` 제약명 확인:
   ```sql
   SELECT conname FROM pg_constraint WHERE conrelid='notification_template'::regclass;
   ```
2. `role_permissions` 테이블 컬럼명 — V57 마이그레이션에서 확인
3. `notification_subscription` 테이블 채널 컬럼명 — research.md 참조

---

## 위험 완화 계획

| 위험 | 완화 |
|------|------|
| R1: code UNIQUE → (code, language) 전환 | run 시작 시 제약명 확인 후 DROP IF EXISTS |
| R2: 워커 실발송 시작 | V61 적용 직후 `PENDING` 데이터 없음 확인 |
| R3: PolicyDispatchController 권한 누락 | Phase 1-4에서 가장 먼저 처리 |
| R7: 스레드풀 경합 | DispatchSchedulerConfig에서 별도 빈 강제 |
