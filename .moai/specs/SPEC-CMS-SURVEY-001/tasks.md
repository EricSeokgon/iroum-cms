# SPEC-CMS-SURVEY-001 TDD 구현 태스크 목록

**버전**: 1.0.0  
**작성일**: 2026-06-18  
**개발 방법론**: TDD (RED-GREEN-REFACTOR)  
**커버리지 목표**: 85%+  

---

## 브라운필드 분석 요약

### 기존 코드 현황
- `SurveyServiceImpl.java` (376줄): CRUD + submitResponse + getResults 구현 완료
- `SurveyController.java` (132줄): 7개 엔드포인트 존재 (2개 추가 필요)
- `SurveyResponseMapper.java`: 3개 메서드만 존재 (listBySurveyId 추가 필요)
- `UserNotificationInboxMapper.java`: insert/markRead만 존재 (batch insert 추가 필요)
- `AdminNotificationMapper.java`: insert() 메서드 존재 — 직접 사용 가능
- `QnaNotificationServiceImpl.java`: 이 패턴을 참고해 SurveyNotificationService 구현

### 핵심 설계 결정
1. **알림 트랜잭션 격리**: 설문 트랜잭션과 분리 — 알림 실패 시 설문 롤백 금지 (best-effort)
2. **멱등성**: `survey_notification_log` UNIQUE(survey_id, type) → DuplicateKeyException 캐치 → 경고 로그 후 계속
3. **INAPP 대량 발송**: `INSERT INTO user_notification_inbox SELECT id FROM users WHERE status='ACTIVE'` 단일 SQL
4. **Admin 알림**: `INSERT INTO admin_notification SELECT ur.user_id FROM user_roles ur JOIN roles r ON ... WHERE r.name IN ('ADMIN','SUPER_ADMIN')` 단일 SQL
5. **updateSurvey 상태 감지**: 업데이트 전 기존 status 조회 → 전환 감지 (DRAFT→OPEN, *→CLOSED)

---

## Phase A: 인프라 (V54 마이그레이션)

### TASK-A1: V54 마이그레이션 SQL 작성
**대상 파일**: `backend/src/main/resources/db/migration/V54__survey_notification_and_rbac.sql`  
**연계 AC**: AC-015, AC-016, AC-017, AC-018, AC-019  
**우선순위**: Priority High  

내용:
- `survey_notification_log` 테이블 (id, survey_id, type, status, created_at + UNIQUE(survey_id, type))
- `SURVEY:READ`, `SURVEY:WRITE`, `SURVEY:EXPORT` permissions INSERT
  - SURVEY:READ/WRITE action = 'VIEW'/'MANAGE', SURVEY:EXPORT action = 'EXECUTE'
- role_permissions 매핑: ADMIN/SUPER_ADMIN → 세 권한 모두, CONTENT_ADMIN → SURVEY:READ/WRITE
- system_settings: feature.survey.enabled = 'true'
- admin_menu: "설문관리" 메뉴 항목 + admin_menu_permissions 매핑
- 모든 INSERT에 ON CONFLICT DO NOTHING 적용

---

## Phase B: 도메인 계층 (Java Backend)

### TASK-B1: SurveyNotificationLog 엔티티 생성
**대상 파일**: `backend/src/main/java/kr/co/ircp/cms/domain/board/entity/SurveyNotificationLog.java`  
**연계 AC**: AC-010, AC-011, AC-012  
**우선순위**: Priority High  

QnaNotificationLog 참고, Survey용으로 간소화:
- id, surveyId, type (SURVEY_OPENED/SURVEY_CLOSED/SURVEY_RESPONSE_LIMIT), status (SENT/FAILED), createdAt
- `@Data @Builder @NoArgsConstructor @AllArgsConstructor` (MyBatis POJO)

### TASK-B2: SurveyNotificationLogMapper 인터페이스 + XML 생성
**대상 파일**:  
- `backend/src/main/java/kr/co/ircp/cms/domain/board/repository/SurveyNotificationLogMapper.java`  
- `backend/src/main/resources/mapper/board/SurveyNotificationLogMapper.xml`  
**연계 AC**: AC-010, AC-011, AC-012, AC-013  
**우선순위**: Priority High  

메서드:
- `void insert(SurveyNotificationLog log)` — UNIQUE 제약으로 멱등성 보장
- `List<SurveyNotificationLog> findBySurveyId(Long surveyId)` — 로그 조회

### TASK-B3: SurveyNotificationService 인터페이스 생성
**대상 파일**: `backend/src/main/java/kr/co/ircp/cms/domain/board/service/SurveyNotificationService.java`  
**연계 AC**: AC-010, AC-011, AC-012  
**우선순위**: Priority High  

메서드:
```java
void sendSurveyPublishedNotification(Long surveyId);  // INAPP → 전체 활성 사용자
void sendSurveyClosedAdminNotification(Long surveyId);  // Admin → 모든 관리자
void sendResponseLimitAdminNotification(Long surveyId);  // Admin → 모든 관리자
```

### TASK-B4: [RED] SurveyNotificationServiceImpl 단위 테스트 작성 (실패 상태)
**대상 파일**: `backend/src/test/java/kr/co/ircp/cms/domain/board/service/SurveyNotificationServiceImplTest.java`  
**연계 AC**: AC-010, AC-011, AC-012, AC-013  
**우선순위**: Priority High  

테스트 케이스:
- `sendSurveyPublishedNotification_success`: INAPP 발송 성공 (survey_notification_log에 SENT 기록)
- `sendSurveyPublishedNotification_idempotent`: 중복 발송 시 DuplicateKeyException → 경고 로그, 예외 미전파
- `sendSurveyClosedAdminNotification_success`: Admin 알림 발송 성공
- `sendSurveyClosedAdminNotification_idempotent`: 중복 발송 차단
- `sendResponseLimitAdminNotification_success`: 한도 도달 Admin 알림
- `sendResponseLimitAdminNotification_idempotent`: 중복 발송 차단

### TASK-B5: [GREEN] SurveyNotificationServiceImpl 구현
**대상 파일**: `backend/src/main/java/kr/co/ircp/cms/domain/board/service/SurveyNotificationServiceImpl.java`  
**연계 AC**: AC-010, AC-011, AC-012, AC-013  
**우선순위**: Priority High  

구현 패턴 (QnaNotificationServiceImpl 참고):
```java
@Service @RequiredArgsConstructor @Slf4j
@Transactional
public class SurveyNotificationServiceImpl implements SurveyNotificationService {
    private final SurveyNotificationLogMapper logMapper;
    private final UserNotificationInboxMapper inboxMapper;  // 기존 mapper 재사용
    private final AdminNotificationMapper adminNotificationMapper;  // 기존 mapper 재사용
    private final SurveyMapper surveyMapper;

    @Override
    public void sendSurveyPublishedNotification(Long surveyId) {
        // 1) 멱등성 체크: survey_notification_log INSERT
        try {
            logMapper.insert(SurveyNotificationLog.builder()
                .surveyId(surveyId).type("SURVEY_OPENED").status("SENT").build());
        } catch (DuplicateKeyException e) {
            log.warn("설문 공개 알림 중복 발송 차단: surveyId={}", surveyId);
            return;
        }
        // 2) 전체 활성 사용자에게 INAPP 일괄 발송
        // UserNotificationInboxMapper에 insertBatchForActiveSurveyOpen(Long surveyId, String title, String body) 추가
        // — 단일 INSERT...SELECT SQL
        inboxMapper.insertBatchForActiveSurveyOpen(surveyId, ...);
    }
    // sendSurveyClosedAdminNotification, sendResponseLimitAdminNotification 유사 구현
    // admin: AdminNotificationMapper.insertForAdminRoles(AdminNotification template) 추가
}
```

**주의**: UserNotificationInboxMapper.insertBatchForActiveSurveyOpen + AdminNotificationMapper.insertForAdminRoles 신규 메서드 추가 필요

### TASK-B6: UserNotificationInboxMapper 배치 INSERT 메서드 추가
**대상 파일**:  
- `backend/src/main/java/kr/co/ircp/cms/domain/board/repository/UserNotificationInboxMapper.java`  
- `backend/src/main/resources/mapper/board/UserNotificationInboxMapper.xml`  
**연계 AC**: AC-010  
**우선순위**: Priority High  

신규 메서드:
```java
void insertBatchForActiveSurveyOpen(
    @Param("surveyId") Long surveyId,
    @Param("title") String title,
    @Param("body") String body
);
```
SQL: `INSERT INTO user_notification_inbox (user_id, type, title, body, ref_id, ref_type) SELECT id, 'SURVEY_OPENED', #{title}, #{body}, #{surveyId}, 'SURVEY' FROM users WHERE status = 'ACTIVE'`

### TASK-B7: AdminNotificationMapper 관리자 일괄 INSERT 메서드 추가
**대상 파일**:  
- `backend/src/main/java/kr/co/ircp/cms/domain/notification/admin/repository/AdminNotificationMapper.java`  
- `backend/src/main/resources/mapper/notification/AdminNotificationMapper.xml`  
**연계 AC**: AC-011, AC-012  
**우선순위**: Priority High  

신규 메서드:
```java
void insertForAdminRoles(
    @Param("type") String type,
    @Param("severity") String severity,
    @Param("title") String title,
    @Param("body") String body,
    @Param("refId") Long refId
);
```
SQL: `INSERT INTO admin_notification (admin_user_id, type, severity, title, body, ref_type, ref_id, status) SELECT ur.user_id, #{type}, #{severity}, #{title}, #{body}, 'SURVEY', #{refId}, 'UNREAD' FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE r.name IN ('ADMIN', 'SUPER_ADMIN')`

### TASK-B8: SurveyService 인터페이스 — 신규 메서드 추가
**대상 파일**: `backend/src/main/java/kr/co/ircp/cms/domain/board/service/SurveyService.java`  
**연계 AC**: AC-001, AC-003, AC-006  
**우선순위**: Priority High  

추가 메서드:
```java
PageResponse<SurveyResponseItem> getResponses(Long surveyId, int page, int size);
byte[] exportResults(Long surveyId);  // UTF-8 BOM CSV
```

### TASK-B9: SurveyResponseItem DTO 생성
**대상 파일**: `backend/src/main/java/kr/co/ircp/cms/domain/board/dto/SurveyResponseItem.java`  
**연계 AC**: AC-006, AC-007, AC-008  
**우선순위**: Priority High  

```java
public record SurveyResponseItem(
    Long responseId,
    Long respondentId,          // nullable (익명)
    String respondentName,      // nullable
    Instant submittedAt,
    List<SurveyAnswerDetail> answers
) {}

public record SurveyAnswerDetail(
    Long questionId,
    String questionText,
    String questionType,
    String answerText            // JSON or raw, 프론트에서 파싱
) {}
```

### TASK-B10: SurveyResponseMapper — listBySurveyId 추가
**대상 파일**:  
- `backend/src/main/java/kr/co/ircp/cms/domain/board/repository/SurveyResponseMapper.java`  
- `backend/src/main/resources/mapper/board/SurveyResponseMapper.xml`  
**연계 AC**: AC-006, AC-007, AC-008  
**우선순위**: Priority High  

신규 메서드:
```java
List<SurveyResponseItem> listBySurveyId(
    @Param("surveyId") Long surveyId,
    @Param("offset") int offset,
    @Param("size") int size
);
long countBySurveyId(@Param("surveyId") Long surveyId);
```
XML: survey_response JOIN survey_answer JOIN survey_question 조인, 페이지네이션 포함

### TASK-B11: [RED] SurveyServiceImpl 신규 기능 단위 테스트 (실패 상태)
**대상 파일**: `backend/src/test/java/kr/co/ircp/cms/domain/board/service/SurveyServiceImplTest.java`  
**연계 AC**: AC-001, AC-003, AC-006, AC-010, AC-011, AC-012  
**우선순위**: Priority High  

테스트 케이스:
- `getResponses_success`: 페이징된 응답 목록 반환
- `getResponses_anonymousSurvey`: respondentId = null 처리
- `exportResults_success`: UTF-8 BOM CSV byte[] 반환, 첫 3바이트 = BOM
- `updateSurvey_draftToOpen_triggersNotification`: DRAFT→OPEN 전환 시 notificationService 호출
- `updateSurvey_toClose_triggersAdminNotification`: *→CLOSED 전환 시 admin 알림 호출
- `submitResponse_limitReached_triggersAdminNotification`: 한도 도달 시 admin 알림 호출
- 알림 실패 시 survey 작업 미실패 (best-effort 확인)

### TASK-B12: [GREEN] SurveyServiceImpl — 알림 통합 + 신규 메서드 구현
**대상 파일**: `backend/src/main/java/kr/co/ircp/cms/domain/board/service/SurveyServiceImpl.java`  
**연계 AC**: AC-001, AC-003, AC-006, AC-010, AC-011, AC-012  
**우선순위**: Priority High  

변경 사항:
1. `SurveyNotificationService` 의존성 주입 추가
2. `updateSurvey`: 업데이트 전 기존 status 조회 → 전환 감지 후 best-effort 알림 호출
3. `submitResponse`: 한도 도달 감지 후 best-effort admin 알림 호출
4. `getResponses(Long surveyId, int page, int size)`: SurveyResponseMapper 위임
5. `exportResults(Long surveyId)`: CSV 생성 (UTF-8 BOM: `{0xEF, 0xBB, 0xBF}`)

알림 best-effort 패턴:
```java
try {
    surveyNotificationService.sendSurveyPublishedNotification(id);
} catch (Exception e) {
    log.warn("설문 공개 알림 발송 실패 (best-effort): surveyId={}", id, e);
}
```

### TASK-B13: SurveyController — 신규 엔드포인트 추가
**대상 파일**: `backend/src/main/java/kr/co/ircp/cms/domain/board/controller/SurveyController.java`  
**연계 AC**: AC-001, AC-003, AC-006, AC-007, AC-008  
**우선순위**: Priority High  

신규 엔드포인트:
```java
@GetMapping("/{id}/responses")
@PreAuthorize("hasAuthority('SURVEY:READ') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
public ResponseEntity<PageResponse<SurveyResponseItem>> getResponses(
    @PathVariable Long id,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size) { ... }

@GetMapping("/{id}/results/export")
@PreAuthorize("hasAuthority('SURVEY:EXPORT') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public ResponseEntity<byte[]> exportResults(@PathVariable Long id) {
    // Content-Disposition: attachment; filename="survey-{id}-results.csv"
    // Content-Type: text/csv;charset=UTF-8
}
```

### TASK-B14: [RED] SurveyControllerIT — 신규 엔드포인트 통합 테스트 (실패 상태)
**대상 파일**: `backend/src/test/java/kr/co/ircp/cms/domain/board/SurveyControllerIT.java`  
**연계 AC**: AC-001, AC-003, AC-006, AC-007, AC-008, AC-015~019  
**우선순위**: Priority High  

테스트 케이스:
- `getResponses_withSurveyReadPermission_200`: SURVEY:READ 권한으로 응답 목록 조회
- `getResponses_withoutPermission_403`: 무권한 403
- `exportResults_withSurveyExportPermission_200`: CSV 다운로드 성공
- `exportResults_withoutExportPermission_403`: SURVEY:READ 있어도 EXPORT 없으면 403
- `exportResults_csvHasBom`: 응답 첫 3바이트 = UTF-8 BOM

---

## Phase C: 프론트엔드 (Vue 3 / TypeScript)

### TASK-C1: survey.ts — 신규 타입 및 API 함수 추가
**대상 파일**: `frontend/admin/src/api/survey.ts`  
**연계 AC**: AC-006, AC-007, AC-008, AC-001, AC-004  
**우선순위**: Priority High  

추가 내용:
```typescript
export interface SurveyAnswerDetail {
  questionId: number
  questionText: string
  questionType: string
  answerText: string | null
}

export interface SurveyResponseItem {
  responseId: number
  respondentId: number | null
  respondentName: string | null
  submittedAt: string
  answers: SurveyAnswerDetail[]
}

// API 함수
export const getSurveyResponses = (surveyId: number, page = 0, size = 20): Promise<PageResponse<SurveyResponseItem>>
export const exportSurveyResults = (surveyId: number): Promise<Blob>  // CSV blob 반환
```

### TASK-C2: SurveyResultsView.vue 생성 (차트 시각화)
**대상 파일**: `frontend/admin/src/views/survey/SurveyResultsView.vue`  
**연계 AC**: AC-001, AC-002, AC-003, AC-004, AC-005  
**우선순위**: Priority High  

구현 내용:
- SURVEY:READ 또는 ADMIN 역할 필요 (라우터 가드)
- `getSurveyResults(id)` 호출 → 질문별 분포 표시
- Element Plus el-card + el-progress 또는 차트 컴포넌트 사용
- SINGLE/MULTI: 막대 차트 (옵션별 응답 수 + %)
- RATING: 별점 분포 표시
- TEXT: 응답 수만 표시
- DATE: 날짜별 분포
- CSV 내보내기 버튼 (SURVEY:EXPORT 권한 보유 시만 활성화)
- `exportSurveyResults(id)` 호출 → Blob URL로 자동 다운로드

### TASK-C3: SurveyResponsesView.vue 생성 (개별 응답 목록)
**대상 파일**: `frontend/admin/src/views/survey/SurveyResponsesView.vue`  
**연계 AC**: AC-006, AC-007, AC-008, AC-009  
**우선순위**: Priority High  

구현 내용:
- SURVEY:READ 또는 ADMIN 역할 필요
- `getSurveyResponses(id, page, size)` 페이지네이션 호출
- el-table: 응답ID, 응답자(익명이면 "익명"), 제출일시, 상세보기 버튼
- 상세 클릭 시 el-drawer 또는 el-dialog로 개별 응답 표시
- 익명 설문: respondentId/Name 모두 "익명" 표시

### TASK-C4: SurveyRespondView.vue — KWCAG 2.2 AA 접근성 패치
**대상 파일**: `frontend/admin/src/views/survey/SurveyRespondView.vue`  
**연계 AC**: AC-020, AC-021, AC-022, AC-023  
**우선순위**: Priority Medium  

패치 내용 (KWCAG 2.2 AA 기준):
- 모든 입력 폼에 aria-label 또는 aria-labelledby 추가
- 라디오/체크박스 그룹에 role="group" + aria-labelledby
- 필수 입력 필드: aria-required="true"
- 에러 메시지: aria-live="polite" + aria-describedby 연결
- 키보드 네비게이션 확인 (Tab 순서, Enter/Space 동작)
- RATING 질문: 별점 UI에 aria-label 추가

### TASK-C5: 라우터 — 신규 뷰 경로 등록
**대상 파일**: `frontend/admin/src/router/index.ts`  
**연계 AC**: AC-001, AC-006  
**우선순위**: Priority High  

추가 라우트:
```typescript
{
  path: '/survey/:id/results',
  name: 'SurveyResults',
  component: () => import('@/views/survey/SurveyResultsView.vue'),
  meta: { requiresAuth: true, permissions: ['SURVEY:READ'] }
},
{
  path: '/survey/:id/responses',
  name: 'SurveyResponses',
  component: () => import('@/views/survey/SurveyResponsesView.vue'),
  meta: { requiresAuth: true, permissions: ['SURVEY:READ'] }
}
```

### TASK-C6: SurveyDetailView.vue — 결과/응답 목록 링크 추가
**대상 파일**: `frontend/admin/src/views/survey/SurveyDetailView.vue`  
**연계 AC**: AC-001, AC-006  
**우선순위**: Priority Medium  

- "결과 보기" 버튼 → `/survey/:id/results`
- "응답 목록" 버튼 → `/survey/:id/responses`
- 버튼 표시 조건: status = 'OPEN' 또는 'CLOSED'

---

## Phase D: 테스트 (검증)

### TASK-D1: SurveyResultsView.spec.ts — 컴포넌트 단위 테스트
**대상 파일**: `frontend/admin/src/views/survey/__tests__/SurveyResultsView.spec.ts`  
**연계 AC**: AC-001, AC-004, AC-005  
**우선순위**: Priority Medium  

테스트:
- 결과 로딩 및 차트 렌더링
- SURVEY:EXPORT 없으면 내보내기 버튼 비활성화
- 내보내기 클릭 시 Blob 다운로드 트리거

### TASK-D2: SurveyResponsesView.spec.ts — 컴포넌트 단위 테스트
**대상 파일**: `frontend/admin/src/views/survey/__tests__/SurveyResponsesView.spec.ts`  
**연계 AC**: AC-006, AC-007, AC-008  
**우선순위**: Priority Medium  

테스트:
- 응답 목록 테이블 렌더링
- 페이지네이션 동작
- 익명 응답 "익명" 표시

---

## 구현 순서 (의존성 그래프)

```
TASK-A1 (V54 SQL)
    ↓
TASK-B1 → TASK-B2 → TASK-B3 → TASK-B4 (RED) → TASK-B5 (GREEN)
TASK-B6, TASK-B7 (병렬 — B5와 동시)
TASK-B8 → TASK-B9 → TASK-B10 → TASK-B11 (RED) → TASK-B12 (GREEN)
TASK-B13 → TASK-B14 (RED/GREEN)
    ↓
TASK-C1 → TASK-C5 (병렬)
TASK-C2 → TASK-C3 → TASK-C4 → TASK-C6 (순차)
    ↓
TASK-D1, TASK-D2 (병렬)
```

---

## 파일 소유권 맵 (File Ownership Map)

| 파일 | 태스크 | 신규/수정 |
|------|--------|--------|
| V54__survey_notification_and_rbac.sql | A1 | 신규 |
| SurveyNotificationLog.java | B1 | 신규 |
| SurveyNotificationLogMapper.java + .xml | B2 | 신규 |
| SurveyNotificationService.java | B3 | 신규 |
| SurveyNotificationServiceImpl.java | B5 | 신규 |
| SurveyNotificationServiceImplTest.java | B4 | 신규 |
| UserNotificationInboxMapper.java + .xml | B6 | 수정 |
| AdminNotificationMapper.java + .xml | B7 | 수정 |
| SurveyService.java | B8 | 수정 |
| SurveyResponseItem.java | B9 | 신규 |
| SurveyResponseMapper.java + .xml | B10 | 수정 |
| SurveyServiceImplTest.java | B11 | 신규/수정 |
| SurveyServiceImpl.java | B12 | 수정 |
| SurveyController.java | B13 | 수정 |
| SurveyControllerIT.java | B14 | 신규/수정 |
| survey.ts | C1 | 수정 |
| SurveyResultsView.vue | C2 | 신규 |
| SurveyResponsesView.vue | C3 | 신규 |
| SurveyRespondView.vue | C4 | 수정 |
| router/index.ts | C5 | 수정 |
| SurveyDetailView.vue | C6 | 수정 |
| SurveyResultsView.spec.ts | D1 | 신규 |
| SurveyResponsesView.spec.ts | D2 | 신규 |

**총 파일 수**: 신규 11개 + 수정 11개 = 22개
