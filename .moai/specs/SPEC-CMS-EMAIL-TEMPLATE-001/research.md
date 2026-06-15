---
id: SPEC-CMS-EMAIL-TEMPLATE-001
title: 이메일 템플릿 관리 — 사전 조사
type: research
created: 2026-06-16
---

# Research — SPEC-CMS-EMAIL-TEMPLATE-001

본 SPEC이 다루는 영역의 기존 코드/인프라 조사 결과. 모든 항목은 본 세션에서 실제
파일·디렉터리·의존성을 확인한 결과이며, 발견된 패턴과 연동 지점을 정리한다.

## 1. 기존 인프라 (재사용 가능)

| 항목 | 상태 | 비고 |
|------|------|------|
| `spring-boot-starter-mail` | 이미 존재 | `backend/build.gradle.kts`에서 확인. `JavaMailSender` 사용 가능 |
| `spring-boot-starter-thymeleaf` | 미설치 | 신규 추가 필요. 변수 치환용 `TextTemplateEngine` |
| 현재 이메일 발송 | 평문만 | `SimpleMailMessage` + 하드코딩 문자열 (`String.format`) |
| 알림 시스템 | 다채널 | EMAIL + INAPP, `@Async`, 재시도(3회), 옵트아웃, 멱등 설계 |
| 이메일 PII 암호화 | 존재 | `EmailEncryptionService` (encrypt/decrypt/computeHmac) |

## 2. 검증된 파일 경로

- 도메인 루트: `backend/src/main/java/kr/co/ircp/cms/domain/`
- 도메인 목록(확인): `ai, audit, auth, board, content, dashboard, governance,
  media, notification, policy, safety, search, security, system`
  → 이메일 템플릿은 신규 `email/` 도메인으로 추가
- 통합 대상 서비스:
  - `auth/service/EmailServiceImpl.java` — OTP/비밀번호 재설정
  - `board/service/QnaNotificationServiceImpl.java` — Q&A 답변 알림(EMAIL 채널)
  - `security/pii/EmailEncryptionService.java` — 수신자 이메일 암호화

## 3. 검증된 시그니처/패턴

### EmailServiceImpl (auth)
- `private final JavaMailSender mailSender;`
- `@Value` 주입 `fromAddress`
- `@Async("auditExecutor")` 비동기 발송 — 실패 시 예외가 호출자에게 전파되지 않고
  로깅만 함 (`@MX:WARN` 주석 존재)
- 메서드: `sendOtp(String to, String code, VerificationPurpose purpose)`,
  `sendPasswordResetNotice(String to)`
- 본문은 `SimpleMailMessage` + `String.format(...)` 하드코딩
- 연동 함의: 비동기·예외 미전파 특성 유지 → fallback 발송도 같은 방식이어야 함

### QnaNotificationServiceImpl (board)
- `notifyAnswered(qnaId, questionerId, answererId)` — EMAIL은
  `qna_notification_optout` 확인 후 발송
- `sendChannel(...)` → `case "EMAIL" -> sendEmail(item)`
- `sendEmail(...)`은 수신자 이메일 PII 복호화 후 `JavaMailSender`로 전송
- 멱등 설계: 중복 발송 차단 로그(`Q&A 알림 중복 발송 차단`) 존재
- `retryFailed()` 재시도 진입점 존재
- 연동 함의: 템플릿 렌더링은 `sendEmail` 내부 본문 생성 지점만 교체

### EmailEncryptionService (security/pii)
- `EncryptedEmail encrypt(String plaintext)`
- `String decrypt(EncryptedEmail encrypted)`
- `String computeHmac(String plaintext)`
- 연동 함의: 발송 로그의 `recipient_email`은 본 서비스로 암호화 저장

## 4. 아키텍처 레이어링 패턴 (notification/admin 기준)

`notification/admin` 패키지가 표준 참고 모델:
```
controller/  (AdminNotificationController)
service/     (AdminNotificationService)
repository/  (AdminNotificationMapper, @Mapper)
entity/      (AdminNotification)
dto/         (AdminNotificationDto, MarkAllReadRequest)
exception/   (AdminNotificationNotFoundException)
```
- Service interface + Impl 분리
- Entity: Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- DTO: Request/Response record 분리
- Exception: 도메인별 `RuntimeException` 상속
- `@Transactional` 서비스 메서드
- `@PreAuthorize("hasAuthority('PERMISSION:ACTION')")` 보안

## 5. 데이터베이스

- Flyway 마이그레이션 위치: `backend/src/main/resources/db/migration/`
- 현재 최고 버전: **V54** (`V54__ai_tag_recommendation.sql`) — 본 세션 확인
- 다음 버전: **V55**(스키마: email_template + smtp_config), **V56**(send_log)
- 네이밍: `V{n}__설명.sql`, 순차 정수, 파일 상단 `-- SPEC-CMS-XXX` 주석 스타일

## 6. 프론트엔드

- 관리자: Vue 3 + TypeScript + Element Plus + Pinia
- 참고 패턴: `BoardListView.vue`(테이블+페이지네이션) +
  `BoardFormView.vue`(el-dialog 폼)
- 라우터: `frontend/admin/src/router/index.ts`
- 메뉴: `frontend/admin/src/layout/`

## 7. 통합 지점 요약 (Integration Points)

1. `EmailServiceImpl.sendOtp` / `sendPasswordResetNotice` → 템플릿 리졸버 사용,
   미존재 시 기존 `String.format` fallback (비동기·예외 미전파 유지).
2. `QnaNotificationServiceImpl.sendEmail` → `QNA_ANSWER` 템플릿 사용, 미존재 시
   기존 본문 fallback. 멱등·옵트아웃·재시도 로직은 변경하지 않음.
3. `EmailEncryptionService` → 발송 로그 수신자 암호화에 재사용.
4. `JavaMailSender` → DB `smtp_config` 기반 동적 재구성 (`JavaMailSenderImpl`).

## 8. 리스크 / 주의

- R1. `@Async` 발송은 예외가 호출자에게 전파되지 않음 → 발송 로그 기록은 비동기
  컨텍스트 내부에서 수행해야 누락 방지.
- R2. SMTP 동적 재구성 시 진행 중 발송과의 동시성 — 활성 설정 교체는 원자적으로.
- R3. fallback 경로가 깨지면 기존 OTP/Q&A 메일 회귀 → MockMailSender 기반 회귀
  테스트 필수.
- R4. Thymeleaf 추가로 인한 기존 뷰/리졸버 자동설정 충돌 가능 →
  `TextTemplateEngine`을 독립 Bean으로 분리하여 영향 최소화.
