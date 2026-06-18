---
id: SPEC-CMS-SURVEY-001
type: compact
updated: 2026-06-18
---

# SPEC-CMS-SURVEY-001 요약 (1-page)

**제목**: 설문조사 결과 시각화 및 알림 연동 (브라운필드 확장)
**상태**: Draft

## 핵심 결정
- **브라운필드**: 설문 백엔드 CRUD(V20)와 관리자 List/Detail 뷰·인라인 질문빌더·공개 응답 폼은 **이미 구현됨**. 신규 작성 금지.
- **실제 갭만 구현**: ① 결과 차트+CSV(SurveyResultsView 신규) ② 응답 개별 열람(SurveyResponsesView 신규 + `/responses` API) ③ 알림 연동(SurveyNotificationService) ④ V54(권한·설정·메뉴·로그).
- **기존 API 재사용**: `/surveys`, `/{id}`, `/{id}/results`, `POST /{id}/responses` 그대로. 결과 차트는 프런트 가공.

## 의뢰서 대비 정정 (실측)
- SurveyStatus = `DRAFT/OPEN/CLOSED/HIDDEN` (PUBLISHED/ARCHIVED 아님). "발행"=`OPEN`.
- QuestionType = `SINGLE/MULTI/TEXT/RATING/DATE`.
- List/Detail/QuestionBuilder/공개폼 = 이미 존재 → SurveyFormView·QuestionBuilder 신규 불필요.
- 공개 응답 = admin SPA 내 `views/public/SurveyRespondView.vue` (`frontend/public` 별도 SPA에는 없음).
- 메뉴 테이블 = `admin_menu`/`admin_menu_permissions` (admin_menu_catalog 아님).
- `SURVEY:EXPORT` action 컬럼 = `EXECUTE` (CHECK 제약), 코드명만 EXPORT.
- 최신 마이그레이션 V53 → 신규 **V54**.

## 알림 모수 (HARD)
- 시민 발행 알림 → `UserNotificationInbox`(domain.board), type=`SURVEY_OPENED`.
- 관리자 알림 → `AdminNotification`(notification.admin), severity=INFO, type=`SURVEY_CLOSED`/`SURVEY_RESPONSE_LIMIT`.
- 멱등: `survey_notification_log` UNIQUE(survey_id,type) + DuplicateKeyException catch.
- best-effort: 알림 호출 try-catch, 설문 트랜잭션 롤백 금지.
- 이메일 미발송.

## V54 내용 (단일 마이그레이션)
권한 `SURVEY:READ/WRITE/EXPORT` + role 매핑 / system_setting `survey.max_responses_default=100`,`survey.allow_anonymous=true` / admin_menu "설문관리"(`/board/surveys`)+SURVEY:READ / `survey_notification_log` 테이블. 전부 `ON CONFLICT DO NOTHING`.

## 제외
설문 CRUD 백엔드 / List·Detail·QuestionBuilder 신규 / 별도 시민 SPA 뷰 / 이메일·SMS·푸시 / 설문 다국어 본문 / PDF 내보내기 / 한도 도달 자동종료 / 포인트 적립 / KPI 위젯.

## 요구사항: REQ-SURVEY-001~022 (결과 7 / 응답 3 / 알림 6 / V54 5 / 접근성 1)
## 의존성: V20, V46, V49/V50, V6, SPEC-CMS-POINTS-001(패턴), 알림 아키텍처(모수 분리)
