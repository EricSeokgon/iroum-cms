---
id: SPEC-CMS-SURVEY-001
type: acceptance
updated: 2026-06-18
---

# 인수 기준 — SPEC-CMS-SURVEY-001

각 REQ에 대한 Given-When-Then 인수 시나리오. 모든 기준은 관찰 가능한 증거(HTTP 응답, DB 행, 렌더된 요소, 테스트 출력)로 검증한다.

---

## 결과 시각화

### AC-001 (REQ-SURVEY-001, 002) 결과 뷰 렌더 + 요약
- **Given** `OPEN` 설문에 제출된 응답이 존재하고
- **When** 관리자가 `board/surveys/:id/results`로 이동하면
- **Then** `GET /api/v1/surveys/{id}/results`가 호출되고 총 응답 수·완료율·평균 소요 시간 요약 카드 3개가 렌더된다.

### AC-002 (REQ-SURVEY-003) 선택형 막대 차트
- **Given** 설문에 `SINGLE` 또는 `MULTI` 질문이 있고 응답이 누적되어 있을 때
- **When** 결과 뷰가 로드되면
- **Then** 해당 질문에 선택지별 응답 수 막대 차트가 렌더되고, 막대 합은 질문 응답 수와 일치한다.

### AC-003 (REQ-SURVEY-004) RATING 분포
- **Given** `RATING` 질문이 있을 때
- **When** 결과 뷰가 로드되면
- **Then** 점수별 분포 막대 + 평균 점수가 표시된다.

### AC-004 (REQ-SURVEY-005) TEXT 목록
- **Given** `TEXT` 질문이 있을 때
- **When** 결과 뷰가 로드되면
- **Then** 자유 응답이 목록으로 표시되고 차트는 렌더되지 않는다.

### AC-005 (REQ-SURVEY-006, 007) CSV 내보내기 + 권한
- **Given** `SURVEY:EXPORT` 권한 보유 관리자로 로그인했을 때
- **When** CSV 내보내기를 선택하면
- **Then** UTF-8 BOM CSV 파일이 다운로드되고 한글이 깨지지 않는다.
- **And Given** `SURVEY:EXPORT` 미보유 관리자일 때 **Then** 버튼이 비활성/숨김이며 직접 `GET /results/export` 호출 시 403이 반환된다.

---

## 응답 개별 열람

### AC-006 (REQ-SURVEY-008) 응답 목록 페이징
- **Given** 설문에 N개의 제출 응답이 있을 때
- **When** 관리자가 응답 뷰를 열면
- **Then** `GET /api/v1/surveys/{id}/responses`(페이징)로 응답자/제출시각/소요시간 컬럼 표가 표시된다.

### AC-007 (REQ-SURVEY-009) 익명 표기
- **Given** `isAnonymous=true` 설문의 응답일 때
- **When** 응답 목록이 표시되면
- **Then** 응답자 열이 "익명"으로 표기되고 respondentId는 노출되지 않는다.

### AC-008 (REQ-SURVEY-010) 답변 펼침
- **Given** 응답 목록에서
- **When** 특정 응답 행을 펼치면
- **Then** 해당 응답의 질문별 답변이 질문 순서대로 표시된다.

---

## 알림 연동

### AC-009 (REQ-SURVEY-011) 발행 → 시민 INAPP
- **Given** `DRAFT` 설문과 활성 사용자 M명이 있을 때
- **When** 관리자가 설문을 `OPEN`으로 전환하면(`updateSurvey`)
- **Then** `UserNotificationInbox`에 type=`SURVEY_OPENED` 행이 활성 사용자 수만큼 INSERT되고 `survey_notification_log`에 (surveyId, `SURVEY_OPENED`, status) 1행이 기록된다.

### AC-010 (REQ-SURVEY-012) 종료 → 관리자 알림
- **Given** `OPEN` 설문을
- **When** 관리자가 `CLOSED`로 전환하면
- **Then** `AdminNotification`에 type=`SURVEY_CLOSED`, severity=`INFO` 행이 INSERT된다.

### AC-011 (REQ-SURVEY-013) 한도 도달 → 관리자 알림
- **Given** `maxResponses=K`, `responseCount=K-1`인 설문에
- **When** 마지막 응답이 제출되어 `responseCount`가 K에 도달하면
- **Then** `AdminNotification`에 type=`SURVEY_RESPONSE_LIMIT` 행이 INSERT된다.

### AC-012 (REQ-SURVEY-014) 멱등
- **Given** (surveyId, `SURVEY_OPENED`) 발송 기록이 이미 존재할 때
- **When** 동일 설문이 다시 `OPEN`으로 전환(또는 재호출)되면
- **Then** 중복 INAPP 발송이 발생하지 않고 `DuplicateKeyException`이 삼켜지며 로그 행이 추가되지 않는다.

### AC-013 (REQ-SURVEY-015) best-effort
- **Given** `SurveyNotificationService`가 예외를 던지도록 강제했을 때
- **When** 관리자가 설문을 `OPEN`으로 전환하면
- **Then** 설문 상태는 `OPEN`으로 정상 커밋되고(트랜잭션 롤백 없음) 경고 로그가 남는다. (검증: IT)

### AC-014 (REQ-SURVEY-016) 이메일 미발송
- **Given** 설문 알림 발송 시
- **When** 어떤 설문 알림이라도 발송되면
- **Then** 이메일 발송 컴포넌트는 호출되지 않는다(인앱/관리자 알림만).

---

## 권한·설정·메뉴·로그 (V54)

### AC-015 (REQ-SURVEY-017) 권한 시드
- **Given** V54 적용 후
- **When** `permissions`를 조회하면
- **Then** `SURVEY:READ`(READ), `SURVEY:WRITE`(WRITE), `SURVEY:EXPORT`(EXECUTE) 3행이 존재한다.

### AC-016 (REQ-SURVEY-018) 설정 시드
- **Given** V54 적용 후
- **When** `system_setting`을 조회하면
- **Then** `survey.max_responses_default`=100(INT), `survey.allow_anonymous`=true(BOOL)가 존재한다.

### AC-017 (REQ-SURVEY-019) 메뉴 시드
- **Given** V54 적용 후
- **When** 관리자 메뉴를 조회하면
- **Then** `admin_menu`에 route `/board/surveys`의 "설문관리" 항목과 `admin_menu_permissions`의 `SURVEY:READ` 매핑이 존재한다.

### AC-018 (REQ-SURVEY-020) 로그 테이블
- **Given** V54 적용 후
- **When** 스키마를 조회하면
- **Then** `survey_notification_log`(id, survey_id, type, status, error_message, created_at, UNIQUE(survey_id,type))가 존재한다.

### AC-019 (REQ-SURVEY-021) 멱등 마이그레이션
- **Given** V54가 한 번 적용된 상태에서
- **When** 동일 시드 SQL을 재실행하면
- **Then** `ON CONFLICT DO NOTHING`으로 중복 키 오류 없이 통과한다.

---

## 접근성

### AC-020 (REQ-SURVEY-022) 공개 응답 폼 KWCAG 2.2 AA
- **Given** 공개 응답 폼(`/public/survey/:id`)에서
- **When** 키보드만으로 폼을 조작하면
- **Then** 모든 입력에 연관 라벨이 있고, 포커스 순서가 논리적이며, 필수 미입력 오류가 `aria-describedby`로 연관되고 제출 성공 메시지가 `aria-live`로 안내된다.

---

## UI 인수 (공통)

- 폼 검증: 필수 질문 미응답 시 제출 차단 및 오류 표시.
- 질문 유형 렌더: `SINGLE`(라디오), `MULTI`(체크박스), `TEXT`(입력), `RATING`(점수), `DATE`(날짜 선택)가 유형별로 올바르게 렌더.
- 차트 렌더: 응답 0건일 때 "응답 없음" 빈 상태 표시(차트 깨짐 없음).

---

## Definition of Done

- [ ] REQ-SURVEY-001~022 전부 AC 충족(증거 첨부).
- [ ] 백엔드 단위·IT 테스트 통과(알림 멱등·best-effort 포함), 커버리지 기준 충족.
- [ ] V54 멱등 재실행 검증 통과.
- [ ] 신규 라우트 2개(results/responses) 권한 가드 동작.
- [ ] 기존 SurveyListView/DetailView/질문빌더 미변경(결과 링크 제외) — 회귀 없음.
- [ ] 설문 알림 이메일 미발송 확인.
- [ ] LSP 0 에러, 린트 통과.
