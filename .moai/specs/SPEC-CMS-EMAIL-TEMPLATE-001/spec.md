---
id: SPEC-CMS-EMAIL-TEMPLATE-001
title: 이메일 템플릿 관리
status: Completed
version: 0.1.0
created: 2026-06-16
updated: 2026-06-23
implemented_at: 2026-06-16
author: ircp
methodology: TDD
priority: High
issue_number: TBD
---

# SPEC-CMS-EMAIL-TEMPLATE-001 — 이메일 템플릿 관리

## HISTORY

- 2026-06-16 (v0.1.0): 최초 작성. 2라운드 Socratic 인터뷰 결과 반영
  (Admin CRUD + JavaMail/Thymeleaf 실발송 + SMTP 동적 설정 + 기존 NotificationService 연동).

---

## 1. Overview (개요)

현재 iroum-cms의 이메일 발송은 `EmailServiceImpl`(OTP/비밀번호 재설정)와
`QnaNotificationServiceImpl`(Q&A 답변 알림)에서 `SimpleMailMessage` 기반 **하드코딩
평문 텍스트**로만 처리된다. 본문 문구를 바꾸려면 코드 수정·재배포가 필요하고, HTML
서식·다국어·미리보기가 불가능하다.

본 SPEC은 관리자가 **HTML 이메일 템플릿을 CRUD로 관리**하고, **Thymeleaf 변수 치환**으로
렌더링하며, **JavaMailSender로 실제 발송**하는 기능을 추가한다. 또한 SMTP 설정을 서버
재시작 없이 동적으로 변경하고, 기존 발송 서비스(`EmailServiceImpl`,
`QnaNotificationServiceImpl`)가 하드코딩 문자열 대신 템플릿을 사용하도록 연동한다.
템플릿이 없을 경우 기존 하드코딩 문구로 **graceful fallback** 하여 회귀를 방지한다.

기반 인프라 재사용:
- `spring-boot-starter-mail` (build.gradle.kts에 이미 존재, `JavaMailSender` 사용 가능)
- `EmailEncryptionService` (수신자 이메일 PII 암호화/복호화/HMAC)
- 신규 의존성: `spring-boot-starter-thymeleaf` (변수 치환용 `TextTemplateEngine`)

---

## 2. Goals & Non-Goals

### Goals (목표)

- G1. 관리자가 HTML 이메일 템플릿을 코드 수정 없이 생성/조회/수정/삭제할 수 있다.
- G2. Thymeleaf 변수 치환으로 동적 본문(제목/HTML/평문)을 렌더링한다.
- G3. 발송 전 미리보기 및 관리자 본인 대상 테스트 발송을 지원한다.
- G4. 기존 발송 서비스가 템플릿 리졸버를 통해 발송하되, 템플릿 부재 시 하드코딩
  문구로 fallback 한다 (기존 동작 보존).
- G5. SMTP 설정을 서버 재시작 없이 변경한다.
- G6. 모든 실발송 결과를 발송 로그에 기록하고 관리자가 조회할 수 있다.

### Non-Goals (비목표)

- NG1. 마케팅 캠페인/대량 메일 발송(스케줄링, 수신자 세그먼트)은 다루지 않는다.
- NG2. WYSIWYG 비주얼 에디터는 제공하지 않는다 (HTML 직접 입력).
- NG3. A/B 테스트, 오픈/클릭 트래킹은 다루지 않는다.
- NG4. 다중 SMTP 프로파일 동시 운용은 다루지 않는다 (단일 활성 설정).
- NG5. 첨부파일 발송은 본 SPEC 범위에서 제외한다.

---

## 3. Requirements (EARS format)

### 3.1 템플릿 CRUD

- **REQ-ET-001** (Event): WHEN 관리자가 신규 템플릿을 등록하면, THEN 시스템은
  `code`(고유키), `name`, `template_type`, `language`, `subject`, `body_html`,
  `body_text`, `variables`(JSONB), `is_active`, 감사필드(`created_by`,
  `created_at`)를 저장해야 한다(SHALL).
- **REQ-ET-002** (Unwanted): IF 등록/수정하려는 `code`+`language` 조합이 이미
  존재하면, THEN 시스템은 중복 오류를 반환하고 저장하지 않아야 한다(SHALL NOT save).
- **REQ-ET-003** (Event): WHEN 관리자가 템플릿 목록을 요청하면, THEN 시스템은
  `template_type`/`language`/`is_active`/키워드 필터와 페이지네이션을 적용한
  결과를 반환해야 한다(SHALL).
- **REQ-ET-004** (Event): WHEN 관리자가 템플릿을 수정하면, THEN 시스템은 변경 내용과
  `updated_by`/`updated_at`을 갱신해야 한다(SHALL).
- **REQ-ET-005** (Event): WHEN 관리자가 템플릿을 삭제하면, THEN 시스템은 해당
  템플릿을 제거하되 발송 로그의 이력은 보존해야 한다(SHALL).
- **REQ-ET-006** (Ubiquitous): 시스템은 `template_type`을 `OTP`, `QNA_ANSWER`,
  `PASSWORD_RESET`, `ADMIN_NOTIFICATION`, `CUSTOM` 중 하나로 제약해야 한다(SHALL).

### 3.2 렌더링

- **REQ-ET-010** (Event): WHEN 템플릿 코드·언어·변수맵으로 렌더링이 요청되면,
  THEN 시스템은 Thymeleaf `TextTemplateEngine`으로 `${변수명}` 자리표시자를 치환한
  제목과 HTML 본문을 생성해야 한다(SHALL).
- **REQ-ET-011** (Unwanted): IF 템플릿의 필수 변수(`variables`에 정의됨)가
  변수맵에 누락되면, THEN 시스템은 렌더링을 거부하고 누락 변수 목록을 포함한 오류를
  반환해야 한다(SHALL NOT render).
- **REQ-ET-012** (State): WHILE 템플릿의 `is_active`가 false인 동안, THEN 시스템은
  해당 템플릿으로의 실발송 요청을 거부해야 한다(SHALL reject).

### 3.3 미리보기 및 테스트 발송

- **REQ-ET-020** (Event): WHEN 관리자가 샘플 변수맵으로 미리보기를 요청하면,
  THEN 시스템은 실제 발송 없이 렌더링된 제목·HTML을 반환해야 한다(SHALL).
- **REQ-ET-021** (Event): WHEN 관리자가 테스트 발송을 요청하면, THEN 시스템은
  렌더링 결과를 **요청 관리자 본인의 이메일**로만 발송해야 한다(SHALL).

### 3.4 기존 발송 서비스 연동

- **REQ-ET-030** (Ubiquitous): 시스템은 `resolveAndRender(templateCode, language,
  variableMap)`를 제공하여 렌더링된 제목+HTML을 반환하는 단일 진입점을 제공해야
  한다(SHALL).
- **REQ-ET-031** (Event): WHEN `QnaNotificationServiceImpl`이 Q&A 답변 알림을
  발송하면, THEN 시스템은 하드코딩 문자열 대신 `QNA_ANSWER` 템플릿을 사용해야
  한다(SHALL).
- **REQ-ET-032** (Event): WHEN `EmailServiceImpl`이 OTP/비밀번호 재설정 메일을
  발송하면, THEN 시스템은 각각 `OTP`/`PASSWORD_RESET` 템플릿을 사용해야 한다(SHALL).
- **REQ-ET-033** (Unwanted): IF 요청한 코드·언어의 활성 템플릿이 존재하지 않으면,
  THEN 시스템은 기존 하드코딩 평문 문구로 fallback 발송하여 기존 동작을 보존해야
  한다(SHALL fallback, NOT fail).

### 3.5 SMTP 동적 설정

- **REQ-ET-040** (Event): WHEN 관리자가 SMTP 설정(host, port, username, password,
  from-address, encryption)을 조회하면, THEN 시스템은 비밀번호를 마스킹한 현재
  설정을 반환해야 한다(SHALL mask password).
- **REQ-ET-041** (Event): WHEN 관리자가 SMTP 설정을 변경하면, THEN 시스템은 서버
  재시작 없이 이후 발송부터 새 설정을 적용해야 한다(SHALL apply without restart).
- **REQ-ET-042** (Unwanted): IF SMTP 설정 변경 시 필수 항목(host, port, from)이
  비어 있으면, THEN 시스템은 변경을 거부해야 한다(SHALL reject).

### 3.6 발송 로그

- **REQ-ET-050** (Event): WHEN 템플릿 기반 실발송이 시도되면, THEN 시스템은
  `template_id`, `recipient_email`(암호화), `subject`, `status`(SUCCESS/FAILED),
  `retry_count`, `sent_at`를 발송 로그에 기록해야 한다(SHALL).
- **REQ-ET-051** (Event): WHEN 관리자가 발송 이력을 조회하면, THEN 시스템은
  템플릿/상태/기간 필터와 페이지네이션을 적용한 결과를 반환해야 한다(SHALL).

### 3.7 보안

- **REQ-ET-060** (Ubiquitous): 시스템은 모든 이메일 템플릿 API에 인증된 관리자
  접근을 요구해야 한다(SHALL).
- **REQ-ET-061** (State): WHILE 조회성 요청인 동안, 시스템은 `EMAIL_TEMPLATE:READ`
  권한을 요구해야 한다(SHALL).
- **REQ-ET-062** (State): WHILE 생성/수정/테스트 발송/SMTP 변경 요청인 동안,
  시스템은 `EMAIL_TEMPLATE:WRITE` 권한을 요구해야 한다(SHALL).
- **REQ-ET-063** (State): WHILE 삭제 요청인 동안, 시스템은 `EMAIL_TEMPLATE:DELETE`
  권한을 요구해야 한다(SHALL).

---

## 4. Acceptance Criteria

각 항목은 대응 REQ를 검증하며, 상세 Given-When-Then 시나리오는 `acceptance.md`에 둔다.

- **AC-ET-001** (→REQ-ET-001/002): 신규 템플릿 등록 시 모든 필드가 저장되고,
  동일 `code`+`language` 재등록은 409로 거부된다.
- **AC-ET-002** (→REQ-ET-003): 목록 조회가 type/language/active/keyword 필터와
  페이지네이션으로 동작한다.
- **AC-ET-003** (→REQ-ET-004/005): 수정 시 `updated_by`/`updated_at`이 갱신되고,
  삭제 후에도 해당 템플릿의 과거 발송 로그가 조회된다.
- **AC-ET-004** (→REQ-ET-006): 허용되지 않은 `template_type`은 400으로 거부된다.
- **AC-ET-005** (→REQ-ET-010): `${name}` 등 변수가 변수맵 값으로 치환된 제목·HTML이
  반환된다.
- **AC-ET-006** (→REQ-ET-011): 필수 변수 누락 시 누락 목록을 포함한 400이 반환되고
  렌더링되지 않는다.
- **AC-ET-007** (→REQ-ET-012): 비활성 템플릿으로의 실발송/테스트 발송은 거부된다.
- **AC-ET-008** (→REQ-ET-020): 미리보기 호출 시 실발송 로그가 생성되지 않는다.
- **AC-ET-009** (→REQ-ET-021): 테스트 발송 시 수신자가 요청 관리자 이메일로 고정된다.
- **AC-ET-010** (→REQ-ET-031/032): Q&A 알림·OTP·비밀번호 재설정이 해당 템플릿
  렌더링 결과로 발송된다(MockMailSender로 검증).
- **AC-ET-011** (→REQ-ET-033): 템플릿 미존재 시 기존 하드코딩 문구로 fallback 발송
  되며 예외가 발생하지 않는다.
- **AC-ET-012** (→REQ-ET-041): SMTP 설정 변경 후 다음 발송이 새 설정으로 수행된다
  (재시작 없이).
- **AC-ET-013** (→REQ-ET-040/042): SMTP 조회 시 password가 마스킹되고, 필수 항목
  누락 변경은 400으로 거부된다.
- **AC-ET-014** (→REQ-ET-050/051): 실발송마다 로그가 기록되고, 이력 조회가
  템플릿/상태/기간 필터로 동작한다.
- **AC-ET-015** (→REQ-ET-060~063): 비인증 접근은 401, 권한 부족은 403으로 거부된다.

---

## 5. Technical Approach

### 5.1 DB Schema (Flyway)

> 현재 최고 버전 V54(`V54__ai_tag_recommendation.sql`) 확인됨. 다음 버전은 V55, V56.

**V55__email_template.sql** (개요):

```sql
-- SPEC-CMS-EMAIL-TEMPLATE-001: 이메일 템플릿 + SMTP 설정
CREATE TABLE email_template (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(100) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    template_type VARCHAR(40)  NOT NULL,   -- OTP|QNA_ANSWER|PASSWORD_RESET|ADMIN_NOTIFICATION|CUSTOM
    language      VARCHAR(10)  NOT NULL DEFAULT 'ko',
    subject       VARCHAR(500) NOT NULL,
    body_html     TEXT         NOT NULL,
    body_text     TEXT,                    -- fallback 평문
    variables     JSONB,                   -- 필수 변수 정의 [{name, required, description}]
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by    BIGINT,
    updated_by    BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_template_code_lang UNIQUE (code, language)
);
CREATE INDEX idx_email_template_type ON email_template(template_type);
CREATE INDEX idx_email_template_active ON email_template(is_active);

-- SMTP 동적 설정 (단일 활성 행 운용; system_config 재사용 대신 전용 테이블)
CREATE TABLE smtp_config (
    id           BIGSERIAL PRIMARY KEY,
    host         VARCHAR(200) NOT NULL,
    port         INT          NOT NULL,
    username     VARCHAR(200),
    password_enc TEXT,                     -- 암호화 저장
    from_address VARCHAR(200) NOT NULL,
    encryption   VARCHAR(20)  NOT NULL DEFAULT 'STARTTLS', -- NONE|SSL|STARTTLS
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by   BIGINT,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

**V56__email_template_send_log.sql** (개요):

```sql
-- SPEC-CMS-EMAIL-TEMPLATE-001: 발송 로그
CREATE TABLE email_template_send_log (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT,                -- 템플릿 삭제 시 NULL 허용(이력 보존)
    template_code   VARCHAR(100),          -- 스냅샷
    recipient_email TEXT NOT NULL,         -- EmailEncryptionService로 암호화
    subject         VARCHAR(500),
    status          VARCHAR(20) NOT NULL,  -- SUCCESS|FAILED
    error_message   TEXT,
    retry_count     INT NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_send_log_template ON email_template_send_log(template_id);
CREATE INDEX idx_email_send_log_status ON email_template_send_log(status);
CREATE INDEX idx_email_send_log_sent_at ON email_template_send_log(sent_at);
```

> 마이그레이션 분리 근거: 스키마(V55)와 로그/이력(V56)을 분리해 점진 적용 및 롤백
> 단위를 명확히 한다.

### 5.2 Backend Layer Breakdown

도메인 패키지: `kr.co.ircp.cms.domain.email` (하위에 `template/admin` 구조).
기존 `notification/admin`의 `controller/service/repository/entity/dto/exception`
레이어링 패턴을 그대로 따른다.

- **Entity**: `EmailTemplate`, `SmtpConfig`, `EmailTemplateSendLog`
  (Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`)
- **DTO** (record): `EmailTemplateCreateRequest`, `EmailTemplateUpdateRequest`,
  `EmailTemplateResponse`, `EmailTemplatePreviewRequest/Response`,
  `TestSendRequest`, `SmtpConfigRequest/Response`, `SendLogResponse`
- **Mapper** (`@Mapper` + XML): `EmailTemplateMapper`, `SmtpConfigMapper`,
  `EmailTemplateSendLogMapper`
- **Service**:
  - `EmailTemplateService` / `Impl`: CRUD, 미리보기, 테스트 발송
  - `EmailTemplateRenderer`: Thymeleaf `TextTemplateEngine` 래퍼, 필수 변수 검증
  - `EmailTemplateResolver`: `resolveAndRender(code, language, vars)` 단일 진입점
    + 미존재 시 fallback 신호 반환
  - `SmtpConfigService` / `Impl`: 동적 SMTP 설정 + `JavaMailSenderImpl` 재구성
  - `EmailTemplateSendLogService` / `Impl`: 로그 기록·조회
- **Controller**: `EmailTemplateAdminController` (`@PreAuthorize`로 권한 강제),
  `SmtpConfigAdminController`
- **Exception**: `EmailTemplateNotFoundException`, `DuplicateTemplateException`,
  `MissingTemplateVariableException`, `TemplateInactiveException`
  (각각 `RuntimeException` 상속)
- **연동 수정**: `QnaNotificationServiceImpl`, `EmailServiceImpl`에
  `EmailTemplateResolver` 주입 후 렌더링 결과 사용, 미존재 시 기존 로직 유지

설정/의존성:
- build.gradle.kts에 `spring-boot-starter-thymeleaf` 추가
- `TextTemplateEngine` Bean 구성 (HTML 모드 + 텍스트 모드)
- SMTP는 DB `smtp_config` 활성 행으로 `JavaMailSenderImpl`을 부팅 시·변경 시 재구성;
  DB 미설정 시 application.yml 기본값 fallback

### 5.3 Frontend Components

관리자(Vue 3 + TS + Element Plus + Pinia). 기존 `BoardListView.vue`(테이블+페이지네이션)
+ `BoardFormView.vue`(el-dialog 폼) 패턴을 따른다.

- `frontend/admin/src/views/email-template/EmailTemplateListView.vue`: el-table,
  필터(type/language/active/keyword), 페이지네이션
- 생성/수정: el-dialog 폼 (HTML 본문은 textarea/코드 입력)
- 미리보기 모달: 샘플 변수 입력 → 렌더링 HTML 표시
- 테스트 발송 버튼
- 발송 로그 섹션/탭: 필터(template/status/기간)
- SMTP 설정 화면 (별도 뷰 또는 탭)
- Router: `frontend/admin/src/router/index.ts`에 `/admin/email-templates` 등록
- Menu: `frontend/admin/src/layout/`에 메뉴 항목 추가
- API 클라이언트: `email-template` API 모듈 추가

---

## 6. Exclusions (What NOT to Build)

- EX1. 대량 발송/캠페인 스케줄러 — Non-Goal NG1.
- EX2. WYSIWYG 비주얼 에디터 — Non-Goal NG2 (HTML 원문 입력만).
- EX3. 오픈/클릭 트래킹·A/B 테스트 — Non-Goal NG3.
- EX4. 다중 SMTP 프로파일 동시 운용 — Non-Goal NG4 (단일 활성 설정).
- EX5. 첨부파일 발송 — Non-Goal NG5.
- EX6. 함수/클래스 시그니처 세부, API 스키마 필드 타입 확정 — Run 단계로 위임.
