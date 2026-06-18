---
id: SPEC-CMS-SURVEY-001
type: plan
updated: 2026-06-18
---

# 구현 계획 — SPEC-CMS-SURVEY-001

## 개요

브라운필드 확장. 기존 설문 백엔드 CRUD + 관리자 List/Detail 뷰 위에 **결과 시각화·응답 열람·알림 연동·V54**만 추가한다. 의뢰서가 "신규 작성"으로 본 List/Detail/QuestionBuilder는 이미 존재하므로 **건드리지 않는다**(spec.md 현황 정정 참조).

방법론: TDD. 백엔드 알림 서비스·응답 조회·CSV는 테스트 우선. 프런트 뷰는 컴포넌트 테스트.

---

## 구현 단계 (우선순위 순)

### 1. V54 마이그레이션 (인프라 선행)
파일: `backend/src/main/resources/db/migration/V54__survey_notification_and_rbac.sql` (신규)
- 권한 시드: `SURVEY:READ`(READ), `SURVEY:WRITE`(WRITE), `SURVEY:EXPORT`(**action=EXECUTE**). `INSERT ... ON CONFLICT (code) DO NOTHING`.
- role_permissions 매핑: 기존 콘텐츠 관리 역할(SUPER_ADMIN/CONTENT_ADMIN/ADMIN 중 적용 대상)에 SURVEY 권한 매핑. `ON CONFLICT DO NOTHING`.
- system_setting 시드: `survey.max_responses_default`=100(INT), `survey.allow_anonymous`=true(BOOL). `ON CONFLICT (key) DO NOTHING`.
- admin_menu 시드: `('board.surveys', '설문관리', 'board'<또는 'content'>, '/board/surveys', ...)`. admin_menu_permissions: `('board.surveys', 'SURVEY:READ')`. `ON CONFLICT DO NOTHING`.
- `survey_notification_log` 테이블 생성:
  ```sql
  CREATE TABLE survey_notification_log (
      id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
      survey_id     BIGINT      NOT NULL,
      type          VARCHAR(50) NOT NULL,
      status        VARCHAR(20) NOT NULL,
      error_message TEXT,
      created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      CONSTRAINT uq_survey_noti UNIQUE (survey_id, type)
  );
  ```
- 단일 마이그레이션 규약: 위 모두 V54 하나에 묶음.

### 2. SurveyNotificationService (백엔드, TDD)
패키지: `kr.co.ircp.cms.domain.board.service` (기존 설문 도메인과 동거)
- 신규: `SurveyNotificationService`(interface) + `SurveyNotificationServiceImpl`.
- 신규: `SurveyNotificationLog` 엔티티 + `SurveyNotificationLogMapper`(@Mapper) + `SurveyNotificationLogMapper.xml`.
- 메서드:
  - `sendSurveyPublishedNotification(Long surveyId)` → 활성 사용자 조회 후 `UserNotificationInbox` 일괄 insert. type=`SURVEY_OPENED`.
  - `sendSurveyClosedAdminNotification(Long surveyId)` → `AdminNotification` insert. type=`SURVEY_CLOSED`, severity=`INFO`.
  - `sendResponseLimitAdminNotification(Long surveyId, int responseCount)` → `AdminNotification` insert. type=`SURVEY_RESPONSE_LIMIT`.
- 멱등: 각 발송 전 `survey_notification_log`에 (surveyId, type) insert 시도, `DuplicateKeyException` catch → no-op. 발송 결과를 log.status에 기록.
- 활성 사용자 조회: 기존 `UserMapper`의 ACTIVE 조회 활용(없으면 broadcast용 조회 메서드 추가).

### 3. SurveyServiceImpl 알림 통합 (best-effort)
파일: `SurveyServiceImpl.java` (확장)
- `updateSurvey()`: 상태가 `OPEN`이 아니었다가 `OPEN`으로 전환될 때만 `sendSurveyPublishedNotification` 호출. 전환 감지를 위해 update 전 기존 status 읽기.
- `submitResponse()`: 카운트 증가 후 `responseCount == maxResponses`이면 `sendResponseLimitAdminNotification` 호출.
- 종료 알림: `CLOSED` 전환 시(updateSurvey) `sendSurveyClosedAdminNotification` 호출.
- [HARD] 모든 알림 호출은 `try { ... } catch (Exception e) { log.warn(...) }`로 감싼다. 알림 실패가 설문 트랜잭션을 롤백하면 안 됨. 이를 검증하는 IT 필수.

### 4. 응답 개별 열람 API (백엔드)
파일: `SurveyController.java`, `SurveyService(Impl)`, `SurveyResponseMapper(.xml)` (확장)
- `GET /api/v1/surveys/{id}/responses?page=&size=` → 페이징 응답 목록(respondentId/익명, submittedAt, 소요시간).
- 응답 펼침용 질문별 답변 포함(또는 별도 `GET /{id}/responses/{responseId}`). 익명이면 응답자 NULL → 프런트 "익명".
- 권한: `SURVEY:READ`.

### 5. CSV 내보내기 (백엔드)
파일: `SurveyController.java` (확장)
- `GET /api/v1/surveys/{id}/results/export` → UTF-8 BOM CSV, `Content-Disposition: attachment`.
- 권한: `SURVEY:EXPORT`(@PreAuthorize hasAuthority).
- 대안: 클라이언트 측 CSV 생성(서버 부하 회피). run 단계에서 응답 규모 보고 결정 — 기본은 서버 측.

### 6. Admin 프런트: SurveyResultsView (신규 뷰 + 라우트)
파일: `frontend/admin/src/views/board/SurveyResultsView.vue` (신규), `router/index.ts` (확장)
- 라우트 `board/surveys/:id/results`, name `board-survey-results`, meta.permissions=['SURVEY:READ'].
- 요약 카드(총 응답/완료율/평균 소요시간) + 질문 유형별 차트.
- 차트: admin SPA에 vue-echarts 도입 여부 확인 후 재사용. 미도입 시 Chart.js 또는 Element Plus 기반 간이 막대. run 단계 결정.
- SurveyDetailView의 기존 결과 다이얼로그 "상세 결과" 링크를 본 뷰로 연결(다이얼로그 자체는 유지).
- CSV 내보내기 버튼(권한 가드).

### 7. Admin 프런트: SurveyResponsesView (신규 뷰 + 라우트)
파일: `frontend/admin/src/views/board/SurveyResponsesView.vue` (신규), `router/index.ts` (확장), `api/survey.ts` (확장: `listSurveyResponses`)
- 라우트 `board/surveys/:id/responses`, name `board-survey-responses`.
- el-table(응답자/익명, 제출시각, 소요시간) + 행 펼침(질문별 답변).

### 8. 공개 응답 폼 접근성 보강 (기존 뷰)
파일: `frontend/admin/src/views/public/SurveyRespondView.vue` (확장)
- KWCAG 2.2 AA: 입력 라벨 연관, 키보드 포커스 순서, 오류 aria-describedby, 제출 성공 메시지 aria-live.
- 기능 신규 추가 아님 — 접근성 속성 보강만.

### 9. 테스트
- 단위: `SurveyNotificationServiceImpl` (발행→INAPP, 종료/한도→관리자, 멱등, best-effort 예외 삼킴).
- 통합(IT): updateSurvey OPEN 전환 시 INAPP 발송 + 알림 실패해도 트랜잭션 커밋. submitResponse 한도 도달 시 관리자 알림.
- 컴포넌트: SurveyResultsView(차트 렌더·유형별 분기), SurveyResponsesView(익명 표기·펼침).
- 마이그레이션: V54 멱등(재실행) 검증.

---

## 파일 소유 맵 (신규 vs 확장)

| 파일 | 구분 |
|---|---|
| `db/migration/V54__survey_notification_and_rbac.sql` | 신규 |
| `domain/board/service/SurveyNotificationService.java` | 신규 |
| `domain/board/service/SurveyNotificationServiceImpl.java` | 신규 |
| `domain/board/entity/SurveyNotificationLog.java` | 신규 |
| `domain/board/repository/SurveyNotificationLogMapper.java` | 신규 |
| `mapper/board/SurveyNotificationLogMapper.xml` | 신규 |
| `domain/board/service/SurveyServiceImpl.java` | 확장(알림 통합) |
| `domain/board/controller/SurveyController.java` | 확장(responses·export) |
| `domain/board/service/SurveyService.java` | 확장 |
| `domain/board/repository/SurveyResponseMapper.java` + `.xml` | 확장(응답 목록) |
| `domain/auth/repository/UserMapper.java` + `.xml` | 확장 가능(활성 사용자 broadcast 조회) |
| `frontend/admin/src/views/board/SurveyResultsView.vue` | 신규 |
| `frontend/admin/src/views/board/SurveyResponsesView.vue` | 신규 |
| `frontend/admin/src/router/index.ts` | 확장(2개 라우트) |
| `frontend/admin/src/api/survey.ts` | 확장(responses·export) |
| `frontend/admin/src/views/board/SurveyDetailView.vue` | 확장(결과 링크만) |
| `frontend/admin/src/views/public/SurveyRespondView.vue` | 확장(접근성) |
| 테스트(백엔드 단위/IT, 프런트 컴포넌트) | 신규 |
| `SurveyListView.vue` / 질문 빌더 / SurveyFormView | **건드리지 않음** |

예상 신규 파일 수: 약 8~10개(의뢰서 추정 25개보다 적음 — List/Detail/Form/QuestionBuilder/public SPA가 이미 존재하기 때문).

---

## 리스크

- **상태 전환 감지**: updateSurvey가 부분 갱신(PUT)이라 status 미포함 update가 가능. 전환 감지는 update 전 기존 status를 읽어 비교해야 정확.
- **활성 사용자 broadcast 규모**: 사용자 수가 크면 INAPP 일괄 insert 성능 이슈. 배치 insert·비동기 검토.
- **차트 라이브러리 미정**: vue-echarts 도입 여부에 따라 6단계 구현 분기. run 시작 시 확인 필요.
- **CSV 인코딩**: 한글 깨짐 방지 위해 UTF-8 BOM 필수.
- **권한 action 제약**: `SURVEY:EXPORT`의 action 컬럼은 `EXECUTE`여야 CHECK 통과(코드명은 EXPORT 유지).
