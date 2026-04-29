# 카카오 알림톡 템플릿 운영 매뉴얼

| 항목 | 내용 |
|------|------|
| 문서 ID | OPS-KAKAO-TEMPLATE-001 |
| 작성일 | 2026-04-29 |
| 작성자 | MoAI orchestrator (사용자 결정 2026-04-29 Q-6 적용) |
| 적용 대상 | iroum-cms 운영팀, 콘텐츠관리자(CONTENT_ADMIN) |
| 정합 SPEC | SPEC-CMS-004 v0.2.1 §13.1 REQ-CONTENT-011-D + §14.1 `notification_template` |
| 정합 SPEC | SPEC-CMS-005 v0.2.1 §13.2 REQ-SYSTEM-008-D-3 (발송 이력 view) |
| 운영 결정 | Q-6 (카카오 알림톡 발급 워크플로 운영 매뉴얼 분리, 2026-04-29) |

---

## 1. 개요

본 문서는 카카오 알림톡 템플릿의 **발급·검수·등록·갱신·운영 모니터링** 절차를 사람·외부 시스템 단계 중심으로 안내한다. SPEC-CMS-004 v0.2.1은 시스템 인터페이스(`notification_template` DDL의 channel/status/kakao_template_code 컬럼·상태 enum·관리자 화면·`review-result` API)만 정의하며, 비즈센터 신청·심사 대응 등 사람 검수 단계는 본 매뉴얼을 따른다.

> **운영 결정 이력 (Q-6)**: SPEC-CMS-004 v0.2 시점에는 카카오 비즈센터 발급 워크플로가 SPEC 본문에 포함되어 있었으나, 사람·외부 시스템 단계의 변동성(카카오 정책 변경·발송 프로필 정책 변경 등)이 SPEC의 안정성을 저해하여 v0.2.1에서 운영 매뉴얼로 분리되었다. SPEC은 시스템 인터페이스(컬럼·상태 enum)만 유지한다.

---

## 2. 사전 준비

### 2.1 카카오 비즈센터 계정

- **계정 등록**: https://business.kakao.com 에서 사업자 계정 등록 (사업자등록증·통신판매업 신고증 필요).
- **승인 소요**: 영업일 기준 1~3일.
- **운영 책임자**: 운영팀장 또는 위임된 콘텐츠관리자 1명을 카카오 비즈센터 master 계정 보유자로 지정.

### 2.2 발신 프로필 등록

- **카카오톡 채널 연동**: 사업자 카카오톡 채널이 있어야 알림톡 발신 프로필을 등록할 수 있다.
- **프로필 ID**: 발급된 프로필 ID(예: `@iroum_official`)는 `application.yml`의 `kakao.bizmessage.sender-key` 또는 `notification_template.kakao_template_code`에 사용되지 않으며, 시스템 환경 변수 `KAKAO_SENDER_KEY`로만 관리한다(개인정보 분리, REQ-CROSS-002 준용).
- **운영팀 책임**: 프로필 ID 발급 후 운영팀 환경 변수 vault에 등록.

### 2.3 카테고리 분류

카카오 알림톡 카테고리는 발송 가능 여부를 결정한다. 본 시스템에서 사용하는 카테고리:

| iroum-cms 분류 | 카카오 카테고리 코드 | 설명 |
|--------------|------------------|------|
| QNA_ANSWER | 003001 (서비스이용) | Q&A 답변 알림 |
| POLICY_MATCH | 003002 (서비스이용) | 정책매칭 결과 안내 |
| PUBLICATION_NEW | 003003 (서비스이용) | 신규 발간자료 안내 |
| ACCOUNT_NOTICE | 002001 (회원/거래) | 계정 관련 변경 알림 |

> **광고성 분류 금지**: iroum-cms의 모든 알림톡은 정보성으로 분류한다. 광고성 분류(004*)는 시스템 정책상 발송 불가.

---

## 3. 템플릿 등록 절차 (7단계)

### Step 1. 요구사항 정리 (콘텐츠관리자)

- 발송 시나리오 정의: 누구에게, 언제, 어떤 내용을 보낼 것인가.
- 발송 채널 결정: KAKAO 알림톡 외에 EMAIL/INAPP fallback이 필요한지 판단.
- 카테고리 매핑: §2.3 표를 따라 카카오 카테고리 코드 결정.

### Step 2. 변수 식별

- 본문 내 동적 치환이 필요한 항목을 `{{변수명}}` 형식으로 추출 (예: `{{user.name}}`, `{{policy.title}}`).
- `notification_template.variables` JSONB에 declare될 schema 작성:
  ```json
  [
    {"name":"user.name","type":"string","required":true,"description":"수신자 이름"},
    {"name":"policy.title","type":"string","required":true,"description":"정책명"}
  ]
  ```
- 변수 개수 제한: 카카오 정책상 1 템플릿당 변수 최대 30개(2026-04 기준).

### Step 3. 비즈센터 신청

- 카카오 비즈센터 → 알림톡 → 템플릿 관리 → 신규 등록.
- 입력 항목: 카테고리, 템플릿명, 본문(변수 포함), 강조표기(선택), 버튼(선택).
- **버튼 정책**: 웹링크·앱링크는 사업자 도메인만 허용. iroum-cms 외부 도메인은 거부됨.
- 신청 후 자동 생성된 카카오 템플릿 코드(예: `K123456789`)를 임시 메모.

### Step 4. 사전 점검 (운영팀 셀프 체크)

신청 직전 다음을 확인하여 거부율을 낮춘다:

- [ ] 본문에 광고성 표현 부재 (할인·이벤트·구매 유도 표현 금지)
- [ ] 변수가 본문 길이의 50% 미만 (변수 위주 광고 회피 정책)
- [ ] 강조표기가 본문 의미를 왜곡하지 않음
- [ ] 발송 시나리오가 §2.3 카테고리와 일치
- [ ] 개인정보(주민등록번호·계좌번호 등)가 본문 또는 변수에 직접 노출되지 않음

### Step 5. 검수 대기 (영업일 1~2일)

- 카카오 검수팀이 자동·수동 검수 진행.
- 시스템 측: `notification_template.status='PENDING_REVIEW'`로 변경(`POST /api/v1/content/notification-templates/{id}/submit-for-review` 호출 시 자동 전환). 본문 수정 잠금(409 + `TEMPLATE_REVIEW_LOCKED`).

### Step 6. 승인/반려 대응

#### 승인 시
- 카카오 비즈센터에서 발급된 `kakao_template_code`(예: `K123456789`)를 시스템에 등록:
  - `POST /api/v1/content/notification-templates/{id}/review-result` body: `{"result":"APPROVED","reviewed_at":"2026-05-01T00:00:00Z","reason":"OK","kakao_template_code":"K123456789"}`
- 시스템: `status='APPROVED'`, `kakao_template_code` UPDATE, `notification_template_history`에 새 version snapshot 적재.

#### 반려 시
- 카카오 비즈센터에서 반려 사유 확인 (Top 5 사유는 §4 참조).
- `POST /api/v1/content/notification-templates/{id}/review-result` body: `{"result":"REJECTED","reviewed_at":"2026-05-01T00:00:00Z","reason":"<카카오 반려 사유>"}`
- 시스템: `status='DRAFT'`로 환원, 본문 잠금 해제, 콘텐츠관리자가 수정 후 Step 3부터 재신청.

### Step 7. 시스템 등록 + 활성화

- `kakao_template_code` 등록 후 `status='APPROVED'` 상태에서 발송 큐가 본 템플릿을 사용 가능.
- A/B 테스트가 필요하면 동일 `code`로 version 분기(`version=2`, `status='DRAFT'`)하여 신규 흐름 시작 (Step 1부터 반복).

---

## 4. 검수 가이드 (광고성 vs 정보성, 거부 사유 Top 5)

### 4.1 광고성 vs 정보성 분류 기준 (카카오 정책)

| 분류 | 허용 여부 | 사례 |
|------|----------|------|
| 정보성 | 허용 | 신청 결과, 답변 알림, 발간자료 등록 안내 |
| 광고성 | iroum-cms 미사용 | 할인·쿠폰·이벤트·신상품 출시 |
| 거래성 | 부분 허용 | 계정 가입 환영, 비밀번호 변경 알림 |

### 4.2 변수 사용 한계

- 변수는 수신자별로 의미 있는 정보만 (이름·정책명·날짜 등).
- 변수 개수 ≤ 30개.
- 변수 비율(변수 글자수 / 전체 본문 글자수) < 50%.
- 변수에 URL을 직접 삽입하지 않음 (URL은 버튼으로 분리).

### 4.3 거부 사유 Top 5 (실 운영 통계 기반, 발생 빈도 순)

1. **광고성 표현 포함** — "특가", "이벤트", "할인" 등 명시적 광고 단어 사용
2. **변수 비율 초과** — 본문이 변수 위주로 구성되어 의미 추론 불가
3. **카테고리 불일치** — 003001 카테고리로 신청했으나 본문이 거래성(002*) 의미
4. **개인정보 직접 노출** — 변수 또는 본문에 주민번호·계좌번호 패턴 발견
5. **외부 도메인 버튼** — 사업자 도메인 외 URL을 버튼에 등록 시도

---

## 5. 승인 후 처리

### 5.1 시스템 등록

- `kakao_template_code` 등록은 §3 Step 6 승인 흐름에 포함됨.
- 등록 후 `notification_template.kakao_template_code IS NOT NULL` 보장 → 발송 큐가 외부 호출 시 본 코드를 카카오 API 요청에 포함.

### 5.2 활성화 토글

- 운영팀이 발송을 일시 중단해야 할 경우 (예: 카카오 정책 변경으로 일시 비호환):
  - 직접 status 변경은 금지(시스템 정합성 훼손).
  - `POST /api/v1/content/notification-templates/{id}/retire` API 사용 → `status='RETIRED'` 전환. 이후 발송 큐는 본 템플릿 사용 불가.
  - 재활성화는 신규 version 등록(`version=N+1`)을 통해 진행.

### 5.3 A/B 테스트 옵션

- 동일 `code`로 `version=1`, `version=2` 두 row를 모두 `status='APPROVED'`로 운영하지 않는다(시스템상 `idx_notif_tmpl_lookup` UNIQUE 제약).
- 대신 `code='QNA_ANSWER_V1'`, `code='QNA_ANSWER_V2'`로 분리한 후 발송 큐의 라우팅 정책으로 분기.
- A/B 결과는 SPEC-CMS-005 v0.2.1 §13.2 `v_notification_history` 뷰의 발송 이력에서 조회 가능.

---

## 6. 변경 관리

### 6.1 템플릿 수정 = 새 코드 발급 (재검수 필수)

- 카카오 정책상 `APPROVED` 템플릿의 본문 수정은 시스템에서 `version=N+1` 신규 row 분기로 처리 (REQ-CONTENT-011-D-5).
- `version=N+1`은 `status='DRAFT'`로 시작 → 카카오에 재신청 → 새 `kakao_template_code` 발급.
- 기존 `version=N`은 잠금 보존 (notification_template_history 활용).

### 6.2 운영 책임자 교체

- 카카오 비즈센터 master 계정 위임이 필요한 경우, 카카오 정책상 별도 위임 절차 진행 (영업일 1~2일).
- 시스템 측에는 영향 없음.

---

## 7. 모니터링·이슈 대응

### 7.1 발송 실패 분석

- SPEC-CMS-005 v0.2.1 §13.2 `v_notification_history` view를 활용 (Q-7 결정으로 INNER JOIN 갱신됨):
  - `GET /api/v1/system/integration-logs/notifications?type=KAKAO&from=...&to=...`
  - 응답: `(channel='KAKAO', status, response_code, error_message, recipient_user_id, sent_at)`
- 실패 사유 Top 3:
  1. `1004` (수신자 미동의) — 사용자가 카카오톡 채널을 차단한 경우
  2. `2010` (휴면) — 수신자 카카오톡 휴면 상태
  3. `9999` (기타) — 카카오 일시 장애 → 자동 재시도 큐(SPEC-CMS-003 v0.2 REQ-BOARD-014-D-3 정책 준용)

### 7.2 재발급 시점

다음 조건 중 하나가 발생하면 운영팀이 신규 version 발급을 검토한다:

- 동일 템플릿 발송 실패율이 7일 평균 5% 초과
- 카카오 정책 변경으로 기존 템플릿이 비호환 처리됨
- 수신자 피드백으로 본문 의미 모호함이 보고됨 (3건 이상)

### 7.3 카카오 정책 변경 추적

- 운영팀은 분기별로 https://business.kakao.com/info/notice 의 알림톡 정책 변경 공지를 검토한다.
- 정책 변경이 시스템 인터페이스에 영향을 주는 경우(예: 카테고리 코드 추가·변경) SPEC-CMS-004 v0.2.x amendment로 반영.

---

## 8. 참고 링크

- 카카오 비즈센터: https://business.kakao.com
- 카카오 알림톡 가이드: https://business.kakao.com/info/bizmessage
- 카카오 알림톡 템플릿 검수 정책 (공식): https://kakaobusiness.gitbook.io/main/ad/bizmessage/notice-friend/content-guide
- iroum-cms SPEC-CMS-004 v0.2.1: `.moai/specs/SPEC-CMS-004/spec.md` §13.1 REQ-CONTENT-011-D + §14.1 `notification_template`
- iroum-cms SPEC-CMS-005 v0.2.1: `.moai/specs/SPEC-CMS-005/spec.md` §13.2 REQ-SYSTEM-008-D-3 (`v_notification_history` view)
- 운영 결정 Q-6: 사용자 결정 2026-04-29 (카카오 알림톡 발급 워크플로 운영 매뉴얼 분리)

---

## 9. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v1.0 | 2026-04-29 | MoAI orchestrator | 초안 작성 (사용자 결정 2026-04-29 Q-6 적용 — SPEC-CMS-004 v0.2 §13.1 REQ-CONTENT-011-D-4의 사람·외부 시스템 단계 워크플로를 본 운영 매뉴얼로 분리). SPEC은 시스템 인터페이스(컬럼·상태 enum) 정의만 유지. |
