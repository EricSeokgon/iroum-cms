# SPEC-CMS-USER-APPROVAL-002 — 구현 계획 (plan)

## 1. 선결 조건 (Blocking Prerequisites)

[HARD] 본 SPEC의 run 단계는 다음이 `main`에 머지된 이후에만 시작한다.

- **SPEC-CMS-USER-APPROVAL-001** (`domain.approval`, `PENDING_APPROVAL`, V58, 일괄 승인/거절)
- **SPEC-CMS-EMAIL-TEMPLATE-001** (`EmailTemplateResolver`, `email_template` V55)
- (SPEC-CMS-002의 `VerificationService`/`SIGNUP`은 이미 main 존재)

run 직전 체크: `ls backend/src/main/java/kr/co/ircp/cms/domain/approval` 존재 확인 + 최신 마이그레이션 번호 재확인 → 그 다음 번호로 V 파일 생성(잠정 V59).

## 2. 기술 접근 (Technical Approach)

### 2.1 이메일 인증 (Area 1) — 연동 위주
- `VerificationService.request/confirm` 재사용, `VerificationPurpose.SIGNUP` 사용.
- `AuthServiceImpl.registerPublicUser` 진입부에 `REGISTRATION_EMAIL_VERIFY_REQUIRED` 설정 분기 추가:
  - 설정 ON → `request.verifiedToken`을 `validateVerifiedToken(token, SIGNUP)`로 검증, 실패 시 예외(403/400).
  - 설정 OFF → 기존 흐름 유지.
- `PublicRegisterRequest`에 `verifiedToken` 필드 additive(설정 OFF 시 무시).
- 인증 코드 발송 채널: 기존 `VerificationService` OTP 채널 단일화. `USER_APPROVAL_VERIFY_CODE` 템플릿은 추가하지 않는다(Section 1.1 [4] 결정).
- register 성공 + verifiedToken 검증 성공 시 `email_verified_at` 컬럼에 현재 시각 기록.

### 2.2 스케줄러 (Area 2) — 순신규
- `domain.approval.job.ApprovalReminderJob` + `ApprovalAutoRejectJob`(또는 단일 `ApprovalLifecycleJob`), `@Component`+`@Scheduled(cron)`.
- 잡은 서비스(`UserApprovalService`)의 신규 메서드(`sendReminders()`, `autoRejectExpired()`)에 1줄 위임(QnaNotificationRetryJob 패턴).
- 임계값은 `SystemSettingService.get()`로 런타임 조회(잡 내 하드코딩 금지). 설정 0/미지정 → no-op.
- 대기열 쿼리: `UserApprovalMapper`에 `selectPendingOlderThan(days, reminderUnsent)` / `selectPendingExceeding(days)` 추가.
- 발송은 afterCommit / `@Async`.

### 2.3 일괄 (Area 3) — 기구현 검증 + 경량 보강
- 백엔드/프론트 일괄 API·UI는 APPROVAL-001 산출물 그대로 사용. 신규 코드 최소화.
- 회귀 테스트 보강(빈 사유 400, 상태 불일치 부분 실패, BulkOperationResult 집계).
- 프론트: `ApprovalQueueView`에 경과일/인증여부 컬럼 + 부분 실패 상세 표시.

### 2.4 데이터 (T0)
- 단일 Flyway 파일: `reminder_sent_at` additive + `email_verified_at` additive + 설정 3종(`REGISTRATION_APPROVAL_REMINDER_DAYS`, `REGISTRATION_APPROVAL_MAX_WAIT_DAYS`, `REGISTRATION_EMAIL_VERIFY_REQUIRED`) + 이메일 템플릿 2종(ko/en): `USER_APPROVAL_REMINDER`, `USER_APPROVAL_AUTO_REJECTED`.
- `USER_APPROVAL_VERIFY_CODE` 템플릿 시드 없음 — VerificationService OTP 채널 재사용.
- 모든 시드 idempotent(`ON CONFLICT DO NOTHING`).

## 3. 마일스톤 (우선순위 기반, 시간 추정 없음)

1. **M1 (High)**: T0 마이그레이션 + T1 register 인증 연동 (가입 인증 코드 완성).
2. **M2 (High)**: T2 리마인더 잡 + T3 자동 거절 잡 (스케줄러 완성).
3. **M3 (Medium)**: T4 일괄 회귀 검증 + T5 프론트 경량 보강.
4. **M4 (High)**: T6 acceptance Given-When-Then 정합 + 전체 테스트 GREEN.

순서: M1 → M2 → M3 → M4. M1·M2는 독립적이나 동일 마이그레이션 파일(T0) 공유 → T0 선행.

## 4. 위험 (Risks)

| 위험 | 영향 | 완화 |
|------|------|------|
| APPROVAL-001 미머지 | run 불가/충돌 | 선결 조건 [HARD] 명시, 머지 후 run |
| 마이그레이션 번호 충돌(V54~58 미머지 브랜치 점유) | Flyway 적용 실패 | run 직전 최신 번호 재확인 후 재번호 |
| OTP 발송 방식과 이메일 템플릿 이원화 | 코드 발송 경로 혼선 | plan에서 OTP 발송 경로 단일화 결정 |
| 스케줄러 다중 노드 중복 발송 | 리마인더 중복 | 단일 노드 가정(Exclusions), `reminder_sent_at` 가드로 멱등 |
| register 인증 필수화가 기존 가입 깨뜨림 | 회귀 | 설정 게이트 기본 OFF, 회귀 테스트 |

## 5. MX 태그 대상

- `AuthServiceImpl.registerPublicUser` — 인증 분기 추가(fan_in 높음) → `@MX:ANCHOR` 유지/갱신.
- 신규 `@Scheduled` 잡 — `@MX:NOTE`(멱등성·설정 게이트 의도).
- `UserApprovalService.autoRejectExpired` — 상태 비가역 전환 → `@MX:WARN` 검토.
