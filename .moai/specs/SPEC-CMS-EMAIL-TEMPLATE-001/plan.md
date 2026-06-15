---
id: SPEC-CMS-EMAIL-TEMPLATE-001
title: 이메일 템플릿 관리 — 구현 계획
type: plan
created: 2026-06-16
methodology: TDD
---

# Implementation Plan — SPEC-CMS-EMAIL-TEMPLATE-001

TDD(RED-GREEN-REFACTOR) 방식. 우선순위 라벨로 순서를 표기한다(시간 추정 없음).

## 1. Task Breakdown (TDD cycles)

### T0 — 의존성·인프라 준비 (Priority: High)
- build.gradle.kts에 `spring-boot-starter-thymeleaf` 추가
- `TextTemplateEngine` Bean 구성(HTML/텍스트 모드)
- RED: 컨텍스트 로드 + Bean 주입 검증 테스트
- GREEN: Bean 등록
- 의존: 없음 (선행 작업)

### T1 — DB 마이그레이션 (Priority: High)
- `V55__email_template.sql` (email_template + smtp_config)
- `V56__email_template_send_log.sql`
- RED: Flyway 마이그레이션 적용 + 스키마 검증 테스트(@SpringBootTest 또는 Testcontainers)
- GREEN: DDL 작성
- 의존: 없음 (T0와 병행 가능)

### T2 — 렌더링 엔진 (Priority: High) → REQ-ET-010/011
- `EmailTemplateRenderer`: 변수 치환 + 필수 변수 검증
- RED: `${name}` 치환 성공, 필수 변수 누락 시 `MissingTemplateVariableException`
- GREEN: Thymeleaf TextTemplateEngine 래퍼 구현
- REFACTOR: 변수 추출/검증 로직 정리
- 의존: T0

### T3 — 템플릿 CRUD (Priority: High) → REQ-ET-001~006
- Entity / Mapper(+XML) / DTO / Service+Impl / Exception
- RED: 등록·중복거부·목록필터·수정·삭제·타입제약 테스트
- GREEN: CRUD 구현 (`@Transactional`)
- REFACTOR: 검색 조건 공통화
- 의존: T1

### T4 — 컨트롤러 + 보안 (Priority: High) → REQ-ET-060~063
- `EmailTemplateAdminController` (`@PreAuthorize`)
- 엔드포인트: list/create/detail/update/delete/preview/test-send/send-logs
- RED: 401/403/권한별 접근 테스트 (MockMvc 또는 @WebMvcTest)
- GREEN: 컨트롤러 구현
- 의존: T2, T3

### T5 — 미리보기 + 테스트 발송 (Priority: Medium) → REQ-ET-020/021/012
- 미리보기: 실발송 없이 렌더링 반환
- 테스트 발송: 요청 관리자 본인 이메일로 고정, 비활성 거부
- RED: 미리보기 로그 미생성, 테스트 수신자 고정, 비활성 거부 테스트(MockMailSender)
- GREEN: 구현
- 의존: T4

### T6 — 발송 로그 (Priority: Medium) → REQ-ET-050/051
- `EmailTemplateSendLog` Entity/Mapper/Service+Impl
- 실발송 시 SUCCESS/FAILED 기록(수신자 암호화), 이력 조회 필터
- RED: 발송 시 로그 1건 기록, 필터 조회 테스트
- GREEN: 구현 (EmailEncryptionService 재사용)
- 의존: T1, T5

### T7 — SMTP 동적 설정 (Priority: Medium) → REQ-ET-040~042
- `SmtpConfigService+Impl`, `SmtpConfigAdminController`
- DB 활성 행 기반 `JavaMailSenderImpl` 재구성, password 마스킹, 필수항목 검증
- RED: 조회 마스킹, 변경 후 다음 발송이 새 설정 반영, 필수누락 거부
- GREEN: 구현 (DB 미설정 시 application.yml fallback)
- 의존: T1
- 주의(R2): 활성 설정 교체는 원자적으로

### T8 — 기존 서비스 연동 (Priority: High) → REQ-ET-030~033
- `EmailTemplateResolver.resolveAndRender(code, language, vars)` 단일 진입점
- `QnaNotificationServiceImpl.sendEmail` → `QNA_ANSWER` 템플릿 사용
- `EmailServiceImpl.sendOtp/sendPasswordResetNotice` → `OTP`/`PASSWORD_RESET` 템플릿
- 미존재 시 기존 하드코딩 fallback (비동기·예외 미전파·멱등·옵트아웃 유지)
- RED: 템플릿 존재 시 렌더링 발송 / 미존재 시 fallback / 회귀 테스트(MockMailSender)
- GREEN: resolver 주입 + 분기
- REFACTOR: 중복 fallback 로직 정리
- 의존: T2, T3, T6
- 주의(R1/R3): 비동기 컨텍스트 내 로그 기록, fallback 회귀 방지

### T9 — 프론트엔드 (Priority: Medium)
- `EmailTemplateListView.vue` (el-table + 필터 + 페이지네이션)
- 생성/수정 el-dialog 폼, 미리보기 모달, 테스트 발송 버튼
- 발송 로그 섹션/탭, SMTP 설정 화면
- Router(`router/index.ts`) + 메뉴(`layout/`) + API 모듈
- 의존: T4~T7 (API 확정 후)

### T10 — 시드 템플릿 + 문서 (Priority: Low)
- 기본 OTP/QNA_ANSWER/PASSWORD_RESET 템플릿 시드 (V55 또는 별도 seed)
- 권한 시드: EMAIL_TEMPLATE:READ/WRITE/DELETE (기존 권한 시드 패턴 확인 후)
- 의존: T1, T8

## 2. File List

### 생성 (Create)
백엔드 (`backend/src/main/java/kr/co/ircp/cms/domain/email/`):
- `template/admin/controller/EmailTemplateAdminController.java`
- `template/admin/controller/SmtpConfigAdminController.java`
- `template/admin/service/EmailTemplateService.java` (+Impl)
- `template/admin/service/EmailTemplateRenderer.java`
- `template/admin/service/EmailTemplateResolver.java`
- `template/admin/service/SmtpConfigService.java` (+Impl)
- `template/admin/service/EmailTemplateSendLogService.java` (+Impl)
- `template/admin/repository/EmailTemplateMapper.java` (+XML)
- `template/admin/repository/SmtpConfigMapper.java` (+XML)
- `template/admin/repository/EmailTemplateSendLogMapper.java` (+XML)
- `template/admin/entity/{EmailTemplate,SmtpConfig,EmailTemplateSendLog}.java`
- `template/admin/dto/*.java` (Request/Response records)
- `template/admin/exception/*.java` (4종)
- 설정: `TextTemplateEngine` Bean config 클래스

마이그레이션:
- `backend/src/main/resources/db/migration/V55__email_template.sql`
- `backend/src/main/resources/db/migration/V56__email_template_send_log.sql`

프론트엔드:
- `frontend/admin/src/views/email-template/EmailTemplateListView.vue` 외 폼/모달
- `frontend/admin/src/api/email-template.ts`

테스트:
- 각 서비스/컨트롤러 단위·통합 테스트, 연동 회귀 테스트(MockMailSender)

### 수정 (Modify)
- `backend/build.gradle.kts` (thymeleaf 의존성)
- `backend/.../auth/service/EmailServiceImpl.java` (resolver 주입)
- `backend/.../board/service/QnaNotificationServiceImpl.java` (resolver 주입)
- `frontend/admin/src/router/index.ts` (라우트)
- `frontend/admin/src/layout/` (메뉴)

## 3. Dependencies (작업 의존도)

```
T0 ─┬─> T2 ─┬─> T4 ─> T5 ─> T6 ─┐
    │       │                   ├─> T8 ─> T10
T1 ─┴─> T3 ─┘                   │
T1 ─────────> T7 ───────────────┘
T4~T7 ──────────────────────────> T9
```

## 4. Priority Order (실행 순서)

1. **High**: T0, T1 (기반) → T2, T3 → T4 → T8 (핵심 연동/회귀 방지)
2. **Medium**: T5 → T6 → T7 → T9
3. **Low**: T10

핵심 회귀 위험은 T8(기존 발송 fallback)이므로 High 묶음에서 MockMailSender 기반
회귀 테스트를 반드시 통과시킨 뒤 Medium으로 진행한다.

## 5. Definition of Done

- 모든 AC-ET-001~015 통과
- 기존 OTP/Q&A/비밀번호 재설정 발송 회귀 없음 (MockMailSender 검증)
- V55/V56 마이그레이션 클린 적용
- TRUST 5 게이트 통과 (테스트 커버리지 포함)
- 프론트 관리자 화면에서 CRUD/미리보기/테스트발송/로그/SMTP 동작 확인
