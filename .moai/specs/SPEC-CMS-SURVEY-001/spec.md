---
id: SPEC-CMS-SURVEY-001
version: 0.1.1
status: Planned
created: 2026-06-18
updated: 2026-06-18
author: ircp
priority: medium
issue_number: 39
---

# SPEC-CMS-SURVEY-001 — 설문조사 결과 시각화 및 알림 연동

## HISTORY

- 2026-06-18 (v0.1.0): 최초 작성(Draft). 브라운필드 확장 SPEC. 기존 코드 실측 결과를 반영하여 범위를 재조정함(아래 "현황 정정" 참조).
- 2026-06-18 (v0.1.1): plan-auditor 감사 반영 — MAJOR 수정(시민 응답 제출 인증 정책 명시), MINOR 6건(REQ-011 주어, SurveyRespondView 현황 정정 추가, REQ-016 UTF-8 BOM, 차트 라이브러리 기준, V번호 충돌 주의, 멱등 키 명시).

---

## 개요 (Goal)

관리자가 설문조사를 생성·배포하고 시민이 응답하는 시스템은 **이미 백엔드 CRUD 및 핵심 관리자 UI까지 구현**되어 있다. 본 SPEC은 그 위에 부족한 세 가지를 채운다.

1. **결과 시각화 강화**: 질문 유형별 차트와 CSV 내보내기, 응답 개별 열람(SurveyResultsView, SurveyResponsesView).
2. **알림 연동**: 설문이 `OPEN`(발행) 상태로 전환될 때 시민 인앱 알림, 종료/한도 도달 시 관리자 운영 알림 발송(`SurveyNotificationService`).
3. **권한·설정·메뉴 정비**: SURVEY 전용 권한, 기본값 system_setting, 관리자 메뉴, 알림 멱등 로그 테이블(V54 마이그레이션).

---

## 현황 정정 (Brownfield Ground Truth)

[HARD] 작업 의뢰서의 일부 전제가 실제 코드와 달라, 본 SPEC은 **실측 코드 기준**으로 작성한다. 구현 단계는 아래 정정 사항을 따른다.

| 항목 | 의뢰서 전제 | 실제 코드 (확정) |
|---|---|---|
| SurveyStatus 값 | DRAFT/PUBLISHED/CLOSED/ARCHIVED | `DRAFT / OPEN / CLOSED / HIDDEN` (`survey.ts`, `SurveyServiceImpl`). "발행"=`OPEN` |
| QuestionType 값 | SINGLE_CHOICE/MULTIPLE_CHOICE | `SINGLE / MULTI / TEXT / RATING / DATE` |
| SurveyListView | 신규 작성 필요 | **이미 존재**(661줄, 상태 필터·생성 다이얼로그·el-table 완비). 라우트 `board/surveys` 등록됨 |
| SurveyDetailView | 신규 작성 필요 | **이미 존재**(627줄, 인라인 질문 빌더·5개 유형·옵션 관리·결과 다이얼로그 완비). 라우트 `board/surveys/:id` |
| SurveyFormView / SurveyQuestionBuilder | 신규 작성 필요 | **불필요**. 생성/수정/질문빌더는 List·Detail 뷰에 인라인으로 이미 구현됨 |
| 공개 응답 뷰 | 별도 `frontend/public` SPA | 실제는 **admin SPA 내** `views/public/SurveyRespondView.vue`(230줄 이미 존재), 라우트 `/public/survey/:id`. `frontend/public/src`(별도 시민 SPA)에는 설문 뷰 **없음**. 본 SPEC은 해당 뷰의 접근성만 보강 |
| 알림 엔티티 패키지 | `UserNotificationInbox` 별도 패키지 | 실제 `kr.co.ircp.cms.domain.board.UserNotificationInbox`, `notification.admin.AdminNotification` |
| 메뉴 테이블 | `admin_menu_catalog` | 실제 `admin_menu` / `admin_menu_permissions`(V49/V50) |
| 권한 action 컬럼 | EXPORT | `permissions.action` CHECK 제약 = READ/WRITE/DELETE/EXECUTE/ADMIN. 코드는 `SURVEY:EXPORT` 가능하나 action 컬럼은 **`EXECUTE`** |
| 최신 마이그레이션 | V53 | V53 확인됨 → 신규 **V54** |

**Why:** 존재하는 661/627줄 뷰를 "신규 작성"으로 잘못 다루면 중복·회귀가 발생한다. 본 정정으로 실제 갭(결과 차트·CSV·개별 응답 열람·알림·V54)만 구현 대상으로 못박는다.

---

## 기술적 접근 (Technical Approach)

브라운필드 확장 전략:

- **기존 REST API 재사용**: `GET /api/v1/surveys`, `/{id}`, `/{id}/results`, `POST /{id}/responses` 그대로 사용. 결과 차트·CSV는 기존 `getResults()` 응답(`SurveyResultDto`)을 프런트에서 가공.
- **신규 결과/응답 뷰는 라우트 추가**: `board/surveys/:id/results`(SurveyResultsView), `board/surveys/:id/responses`(SurveyResponsesView)를 admin 라우터에 등록. 기존 Detail 뷰의 결과 다이얼로그는 유지하되 "상세 결과" 링크를 신규 뷰로 연결.
- **응답 개별 열람 API 신설**: 개별 응답 목록·답변은 기존 API에 없으므로 `GET /api/v1/surveys/{id}/responses`(페이징) 백엔드 추가. 익명 설문은 응답자 "익명" 표기.
- **CSV 내보내기**: `GET /api/v1/surveys/{id}/results/export`(`SURVEY:EXPORT` 권한) — 서버에서 CSV 생성(UTF-8 BOM). 또는 프런트 클라이언트 측 생성. 구현 단계에서 선택(plan.md 참조). PDF는 비범위.
- **알림 best-effort**: `SurveyNotificationService` 호출은 원인 행위(상태 전환·응답 제출)와 **분리된 try-catch**로 감싸 알림 실패가 설문 트랜잭션을 롤백하지 않게 한다([[project-iroum-points-spec-pattern]] best-effort 패턴 계승).
- **알림 멱등**: `survey_notification_log`(surveyId, type) UNIQUE + `DuplicateKeyException` catch로 중복 발송 차단([[project-iroum-ai-tag-spec-pattern]] 로그 패턴 참조).
- **차트 라이브러리**: admin SPA에 이미 `vue-echarts`가 도입되어 있으면 재사용([[project-iroum-notification-arch]] 대시보드 위젯). 미도입 시 plan.md에서 선정.

### 알림 모수 (의미 분리) [HARD]

[[project-iroum-notification-arch]]를 따른다.

- **시민 INAPP 발행 알림** → `UserNotificationInbox`(시민 수신함). type = `SURVEY_OPENED`. 비익명 여부와 무관하게 활성 사용자 대상.
- **관리자 운영 알림** → `AdminNotification`(관리자 수신함). severity = `INFO`. type = `SURVEY_CLOSED` / `SURVEY_RESPONSE_LIMIT`.
- 두 테이블은 의미가 다르므로 절대 혼용하지 않는다.

---

## 요구사항 (EARS Requirements)

### 결과 시각화 (SurveyResultsView)

- **REQ-SURVEY-001** (Event-Driven): **When** 관리자가 설문 상세에서 "상세 결과"를 선택하면, the 시스템 **shall** `board/surveys/:id/results` 경로의 SurveyResultsView를 렌더링하고 `GET /api/v1/surveys/{id}/results`를 호출한다.
- **REQ-SURVEY-002** (Ubiquitous): The SurveyResultsView **shall** 총 응답 수, 완료율(제출 응답 / 시작 응답), 평균 소요 시간을 요약 카드로 표시한다.
- **REQ-SURVEY-003** (State-Driven): **While** 질문 유형이 `SINGLE` 또는 `MULTI`이면, the 시스템 **shall** 선택지별 응답 분포를 막대 차트로 표시한다.
- **REQ-SURVEY-004** (State-Driven): **While** 질문 유형이 `RATING`이면, the 시스템 **shall** 점수 분포를 막대 차트와 평균값으로 표시한다.
- **REQ-SURVEY-005** (State-Driven): **While** 질문 유형이 `TEXT`이면, the 시스템 **shall** 자유 응답을 목록으로 표시한다(차트 없음).
- **REQ-SURVEY-006** (Event-Driven): **When** 관리자가 CSV 내보내기를 선택하면, the 시스템 **shall** `SURVEY:EXPORT` 권한을 검증한 뒤 설문 응답을 UTF-8(BOM) CSV로 다운로드한다.
- **REQ-SURVEY-007** (Unwanted Behavior): **If** `SURVEY:EXPORT` 권한이 없으면, **then** the 시스템 **shall** CSV 내보내기 버튼을 비활성화 또는 숨기고 403을 반환한다.

### 응답 개별 열람 (SurveyResponsesView)

- **REQ-SURVEY-008** (Event-Driven): **When** 관리자가 응답 목록을 요청하면, the 시스템 **shall** `GET /api/v1/surveys/{id}/responses`(페이징)로 응답자, 제출 시각, 소요 시간 컬럼의 표를 표시한다.
- **REQ-SURVEY-009** (State-Driven): **While** 설문이 익명(`isAnonymous=true`)이면, the 시스템 **shall** 응답자 열을 "익명"으로 표기한다.
- **REQ-SURVEY-010** (Event-Driven): **When** 관리자가 특정 응답 행을 펼치면, the 시스템 **shall** 해당 응답의 질문별 답변을 표시한다.

### 알림 연동 (SurveyNotificationService)

- **REQ-SURVEY-011** (Event-Driven): **When** 설문 상태가 `DRAFT`/기타에서 `OPEN`으로 전환되면, the 시스템 **shall** 활성 사용자의 `UserNotificationInbox`에 type=`SURVEY_OPENED` 인앱 알림 레코드를 삽입한다.
- **REQ-SURVEY-012** (Event-Driven): **When** 설문이 종료(`CLOSED`)되면, the 시스템 **shall** 관리자에게 type=`SURVEY_CLOSED`, severity=`INFO` 운영 알림(`AdminNotification`)을 발송한다.
- **REQ-SURVEY-013** (Event-Driven): **When** 응답 제출로 `responseCount`가 `maxResponses`에 도달하면, the 시스템 **shall** 관리자에게 type=`SURVEY_RESPONSE_LIMIT` 운영 알림을 발송한다.
- **REQ-SURVEY-014** (Unwanted Behavior): **If** 동일 설문·동일 알림 유형(`(survey_id, type)` 복합 키)이 이미 `survey_notification_log`에 존재하면, **then** the 시스템 **shall** 재발송하지 않는다(멱등).

- **REQ-SURVEY-014a** (MAJOR — 인증 정책): **When** 시민이 `POST /api/v1/surveys/{id}/responses`로 응답을 제출할 때, the 시스템 **shall** 인증 없이(비로그인) 제출을 허용한다. 단, `isAnonymous=false`인 설문은 Spring Security 인증 컨텍스트에서 respondentId를 추출하며, 로그인하지 않은 경우 401을 반환한다.
- **REQ-SURVEY-015** (Unwanted Behavior): **If** 알림 발송 중 예외가 발생하면, **then** the 시스템 **shall** 예외를 로깅·삼키고 설문 상태 전환/응답 제출 트랜잭션을 롤백하지 **않는다**(best-effort).
- **REQ-SURVEY-016** (Unwanted Behavior): The 시스템 **shall** 설문 알림에 대해 이메일을 발송하지 **않는다**(인앱·관리자 알림 한정).

- **REQ-SURVEY-016a** (Ubiquitous): The CSV 내보내기 응답(`GET /api/v1/surveys/{id}/results/export`) **shall** `Content-Type: text/csv; charset=UTF-8` 헤더와 UTF-8 BOM(`0xEF 0xBB 0xBF`)으로 시작하는 파일을 반환한다(Excel 한글 깨짐 방지).

### 권한·설정·메뉴·로그 (V54)

- **REQ-SURVEY-017** (Ubiquitous): The 시스템 **shall** `SURVEY:READ`, `SURVEY:WRITE`, `SURVEY:EXPORT` 권한을 `permissions` 카탈로그에 보유한다(action: READ/WRITE/EXECUTE).
- **REQ-SURVEY-018** (Ubiquitous): The 시스템 **shall** `survey.max_responses_default`(INT, 기본 100), `survey.allow_anonymous`(BOOL, 기본 true)를 `system_setting`에 보유한다.
- **REQ-SURVEY-019** (Ubiquitous): The 시스템 **shall** 관리자 메뉴(`admin_menu`)에 "설문관리"(route `/board/surveys`)를 게시판/콘텐츠 섹션 하위로 노출하고, `admin_menu_permissions`에 `SURVEY:READ`를 매핑한다.
- **REQ-SURVEY-020** (Ubiquitous): The 시스템 **shall** `survey_notification_log` 테이블로 알림 발송 멱등성과 발송 결과(status, error_message)를 추적한다.
- **REQ-SURVEY-021** (State-Driven): **While** 모든 마이그레이션 시드가 재실행되어도, the 시스템 **shall** `ON CONFLICT DO NOTHING`으로 중복 없이 멱등 적용된다.

### 접근성 (공개 응답)

- **REQ-SURVEY-022** (Ubiquitous): The 공개 설문 응답 폼(`SurveyRespondView`) **shall** KWCAG 2.2 AA를 준수한다(모든 입력에 라벨, 키보드 조작 가능, 오류 메시지 프로그램적 연관).

---

## 제외 범위 (Exclusions / What NOT to Build)

[HARD] 본 SPEC은 아래를 구현하지 않는다.

- **설문 CRUD 백엔드** — 이미 구현됨(`SurveyController`/`SurveyService`/매퍼/V20 스키마). 신규 요구사항 작성 금지.
- **SurveyListView / SurveyDetailView / 질문 빌더 신규 작성** — 이미 존재(661/627줄). 신규 SurveyFormView·SurveyQuestionBuilder 컴포넌트는 만들지 않는다(필요 시 결과 링크만 보강).
- **별도 시민 SPA(`frontend/public`)용 설문 뷰** — 공개 응답은 admin SPA 내 기존 `views/public/SurveyRespondView.vue`로 충분.
- **설문 알림 이메일/SMS/푸시 발송** — 인앱·관리자 알림만.
- **설문 콘텐츠 다국어(i18n) 번역 저장** — UI 라벨 i18n과 무관한 설문 본문 다국어는 비범위.
- **설문 결과 PDF 내보내기** — CSV만.
- **응답 한도 도달 시 자동 종료(상태 자동 CLOSED 전환)** — 알림만 발송, 상태 전환은 관리자 수동.
- **설문 응답에 대한 포인트 적립** — [[project-iroum-points-spec-pattern]] 범위.
- **설문 결과 KPI 위젯 연동** — 비범위(향후 SPEC).

---

## 의존성 (Dependencies)

- **V20** — 기존 설문 스키마(`survey`, `survey_question`, `survey_response`, `survey_answer`). 변경하지 않음.
- **V46** — `notification_delivery_status`(알림 인프라 선행). 본 SPEC은 INAPP/관리자 알림 발송만 사용.
- **V49/V50** — `admin_menu`/`admin_menu_permissions`(메뉴 시드 패턴).
- **V6** — `permissions` 카탈로그(권한 시드 패턴, action CHECK 제약).
- **SPEC-CMS-POINTS-001** — best-effort 적립/알림 패턴 참조([[project-iroum-points-spec-pattern]]).
- **알림 도메인 아키텍처** — [[project-iroum-notification-arch]](모수 분리 [HARD]).

> **V번호 충돌 주의**: 신규 마이그레이션 파일명은 run 단계 착수 시점에 `db/migration/` 디렉토리를 재확인하여 최신 V번호+1을 사용한다. 현재 기준 V53이 최신이며 V54를 계획하나, 동시에 머지된 PR이 있을 경우 번호가 달라질 수 있다.

> **차트 라이브러리 기준**: admin SPA에 `vue-echarts`(또는 ECharts)가 이미 도입되어 있으면 재사용한다. 미도입 시 Element Plus 기본 그래픽 > vue-echarts > Chart.js 순으로 평가하되, 번들 크기 증가가 50KB 미만일 때만 신규 도입을 허용한다(run 단계 확인).
