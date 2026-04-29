# SPEC-CMS-007 Acceptance Criteria — 정책사업 매칭 + 적기 알림

본 문서는 SPEC-CMS-007의 기능·비기능 요구사항(REQ-POLICY-001-D ~ 006-D)에 대한 Given-When-Then 시나리오와 5개 Quality Gate를 정의한다. 본 SPEC `spec.md` §5 EARS 요구사항·§6 REST API·§8 매칭 알고리즘·§9 발송 정책과 1:1 매핑된다.

참조: SPEC-CMS-001 v0.3.2 §15.2 SFR-007/008, SPEC-CMS-004 v0.2.1 §14.2-1 `notification_send`, SPEC-CMS-005 v0.2.1 §14.2 `integration_log` + `v_notification_history` 뷰.

---

## A. 정책 데이터 수집·동기화 (REQ-POLICY-001-D)

### AC-POLICY-001 외부 OpenAPI 정상 동기화

- **Given** `policy_data_source.code='MSS_KSTARTUP'` 이 활성 상태이고 schedule_cron 시점이 도래
- **When** `PolicyImportScheduler`가 외부 OpenAPI를 호출하여 50건의 정책을 수신
- **Then** 50건이 표준 코드 매핑(industry/region) 후 `policy_program`에 INSERT 또는 UPDATE되며, `policy_data_source.last_sync_at`이 갱신되고 `last_status='SUCCESS'`로 기록된다. integration_log 1행이 `integration_type='PUBLIC_DATA'`로 적재된다.

### AC-POLICY-002 외부 API 일시 장애 (재시도)

- **Given** 외부 OpenAPI가 HTTP 503 응답
- **When** PolicyImportScheduler가 호출
- **Then** 5분/30분/4시간 후 3회 재시도하며, 모두 실패 시 `last_status='FAILURE'` 기록 + audit_log severity=CRITICAL + 운영자 알림(이메일) 발송. 기존 `policy_program` row는 변경되지 않는다.

### AC-POLICY-003 중복 정책 식별 (멱등성)

- **Given** 외부 API에서 `source_api_id='K-STARTUP-2026-001'` 정책이 이미 `policy_program`에 존재
- **When** 동일 ID로 갱신 데이터 수신
- **Then** 신규 INSERT 대신 UPDATE되며, 변경 컬럼 diff를 audit_log에 기록한다. 새 id는 발급되지 않는다.

### AC-POLICY-004 표준 코드 매핑 실패 (격리)

- **Given** 외부 정책의 industry 코드가 표준 사전(metadata_dictionary, SPEC-CMS-004)에 없음
- **When** 동기화 실행
- **Then** 해당 정책은 `status='DRAFT'`로 적재되고 `import_warnings` 컬럼에 미매핑 코드가 기록된다. 운영자 화면에 노출되어 수동 매핑 후 `status='ACTIVE'` 전환 가능하다.

### AC-POLICY-005 관리자 수동 보강 (Excel 일괄)

- **Given** SUPER_ADMIN이 50건의 지자체 정책 엑셀(.xlsx) 업로드
- **When** `POST /api/v1/policy/programs/bulk-import` 호출
- **Then** 검증 통과 행만 INSERT, 실패 행은 row 단위 오류 메시지와 함께 응답으로 반환된다. 부분 성공이 허용된다(트랜잭션 단위 = 행 단위).

### AC-POLICY-006 정책 만료 자동 처리

- **Given** `policy_program.application_end < CURRENT_TIMESTAMP` 로 마감일 경과
- **When** 매일 01:00 만료 처리 배치 실행
- **Then** 해당 정책의 `status`가 `ACTIVE → EXPIRED`로 전환되고, 사용자 검색·매칭 결과에서 자동 제외된다.

---

## B. 자격요건 정의·관리 (REQ-POLICY-002-D)

### AC-POLICY-007 자격요건 룰 등록

- **Given** SUPER_ADMIN이 `policy_program.id=100` 의 자격요건을 등록
- **When** `POST /api/v1/policy/programs/100/eligibility-rules` 로 (rule_type='INCLUDE', dimension='INDUSTRY', operator='IN', values='["C26","C27"]', weight=0.30) 전송
- **Then** `policy_eligibility_rule` row 생성, audit_log 적재, 동일 (policy_id, dimension, operator) 조합 충돌 시 409 응답.

### AC-POLICY-008 가중치 합 검증

- **Given** 한 정책의 weight 합이 1.05 (1.0 초과)
- **When** 매칭 시뮬레이션 또는 룰 활성화 시도
- **Then** 시스템은 경고를 반환하되, 가중치 정규화(각 weight ÷ 합) 옵션을 제공하여 운영자가 선택할 수 있게 한다.

### AC-POLICY-009 EXCLUDE 룰 우선

- **Given** 한 정책에 INCLUDE(industry=C26) 룰과 EXCLUDE(region=11000) 룰이 모두 존재하고 사용자는 industry=C26·region=11000
- **When** 매칭 평가
- **Then** EXCLUDE가 우선 적용되어 매칭 점수 0점, TOP N 결과에서 제외된다.

### AC-POLICY-010 키워드 등록 + 가중치 반영

- **Given** SUPER_ADMIN이 `policy_keyword`에 (keyword='탄소중립', weight=0.15)을 등록
- **When** 매칭 평가
- **Then** 사용자 프로필의 `custom_attrs.keywords[]`에 '탄소중립'이 포함되면 보너스 점수 + (0.15 × 100) × 0.05 = 0.75점이 더해진다(상한 5점).

---

## C. 매칭 알고리즘 (REQ-POLICY-003-D)

### AC-POLICY-011 다차원 매트릭스 정상 매칭

- **Given** 사용자(industry=C26, region=11000, employees=50, revenue=5,000,000,000, business_age=36개월)
- **When** `POST /api/v1/policy/match` 호출
- **Then** `policy_eligibility_rule` 통과 정책에 대해 차원별 점수(industry 0.3 / region 0.2 / size 0.2 / age 0.15 / revenue 0.15) 가중 합 산출, 등급(A/B/C/D) 부여, TOP 10 정렬 응답이 반환된다.

### AC-POLICY-012 자격요건 미충족 즉시 제외

- **Given** 사용자 industry=C99(존재 X), 정책 INCLUDE INDUSTRY IN ('C26','C27')
- **When** 매칭 평가
- **Then** 점수 0점, TOP N 결과에서 제외된다(Hard Filter).

### AC-POLICY-013 가중치 차원 점수 계산

- **Given** 사용자 industry=C26 일치, region=불일치, size 일치, age 부분일치(80%), revenue 일치
- **When** 매칭 평가
- **Then** score = 100*0.30 + 0*0.20 + 100*0.20 + 80*0.15 + 100*0.15 = 30+0+20+12+15 = 77점, 등급 B.

### AC-POLICY-014 보너스 점수 적용

- **Given** 위 사용자가 인증 'ISO9001' 보유 + 신규 가입자(7일 이내)
- **When** 매칭 평가
- **Then** 77 + 5(인증) + 3(신규) = 85점, 등급 B.

### AC-POLICY-015 TOP N 동점 처리

- **Given** 두 정책이 모두 점수 85점 동률
- **When** 정렬
- **Then** `matched_at` 최신(즉 더 최근에 등록된 정책)이 상위로 정렬된다.

### AC-POLICY-016 매칭 사유 노출

- **Given** 사용자가 매칭 결과 한 건의 상세 조회 요청
- **When** `GET /api/v1/policy/match/{id}/reason`
- **Then** `score_breakdown` JSONB가 차원별 점수와 충족·미충족 룰 ID 목록을 포함하여 반환된다(설명 가능성 보장).

### AC-POLICY-017 매칭 결과 캐싱 + 무효화

- **Given** 동일 사용자가 1시간 내 재요청
- **When** `POST /api/v1/policy/match`
- **Then** Caffeine 캐시 적중으로 P95 < 50ms 응답. 사용자 프로필 또는 정책 변경 시 캐시 무효화되어 cold path 실행.

### AC-POLICY-018 매칭 결과 만료

- **Given** `policy_match_score.expires_at < CURRENT_TIMESTAMP`
- **When** 사용자 매칭 결과 조회
- **Then** 만료된 결과는 응답에서 제외되고, 자동으로 재매칭이 트리거된다.

---

## D. 발송 예약 + 대상 추출 (REQ-POLICY-004-D)

### AC-POLICY-019 정책 마감 N일 전 자동 트리거

- **Given** `policy_program.application_end - INTERVAL '7 days' = CURRENT_DATE` 이고 dispatch_type='CLOSING_SOON'이 미생성
- **When** 매일 09:00 트리거 배치 실행
- **Then** `notification_dispatch_schedule` 1행 생성(scheduled_at = 마감 7일 전 09:00), target_filter는 매칭 점수 70점 이상 사용자.

### AC-POLICY-020 대상 쿼리 시뮬레이션

- **Given** SUPER_ADMIN이 발송 예약을 생성하기 전 시뮬레이션
- **When** `POST /api/v1/policy/dispatch-schedules/simulate` 에 target_filter 전송
- **Then** 매칭되는 user 수만 응답(개인정보 미반환), 결과 < 1초 내(p95).

### AC-POLICY-021 발송 예약 생성

- **Given** SUPER_ADMIN이 시뮬레이션으로 대상 5,000명을 확인
- **When** `POST /api/v1/policy/dispatch-schedules` 호출
- **Then** `notification_dispatch_schedule` 생성(status='PENDING'), audit_log 적재, 응답으로 schedule_uuid 반환.

### AC-POLICY-022 발송 예약 취소

- **Given** schedule가 status='PENDING' (아직 실행 전)
- **When** SUPER_ADMIN이 `DELETE /api/v1/policy/dispatch-schedules/{uuid}` 호출
- **Then** status='CANCELLED'로 전환, `notification_dispatch_target`에 매핑된 대상이 있다면 함께 무효화. 발송이 진행 중이면 취소 거부(409).

### AC-POLICY-023 대상 추출 (대량)

- **Given** 10만 명 대상 발송 예약
- **When** scheduled_at 도래로 발송 실행 (`PolicyDispatchExecutor`)
- **Then** target_filter 쿼리가 60초 내 완료되어 `notification_dispatch_target` 행 10만 건 INSERT, status='PENDING'.

### AC-POLICY-024 발송 시뮬레이션 비용 안내

- **Given** SUPER_ADMIN이 카카오 알림톡 5,000명 발송 시뮬레이션
- **When** `simulate` 호출
- **Then** 응답에 추정 비용(5,000건 × 7원 = 35,000원) 안내 포함.

---

## E. 알림 발송 — 멱등성·재시도·야간 차단 (REQ-POLICY-005-D)

### AC-POLICY-025 멱등성 — 동일 키 중복 발송 차단

- **Given** `notification_dispatch_target.idempotency_key='abc123'` 이 이미 status='SENT'
- **When** 재시도 배치가 동일 키로 발송 시도
- **Then** UNIQUE 제약으로 중복 INSERT 차단, 추가 발송 0건, audit_log severity=INFO에 'DUPLICATE_BLOCKED' 적재.

### AC-POLICY-026 야간 발송 차단 (21시-08시)

- **Given** scheduled_at='2026-05-01 22:30:00 KST'
- **When** 발송 예약 생성
- **Then** 시스템이 자동으로 scheduled_at을 '2026-05-02 09:00:00 KST'로 보정, 응답에 보정 사실을 안내.

### AC-POLICY-027 발송 실패 자동 재시도

- **Given** 카카오 API HTTP 5xx 응답
- **When** 발송 실행
- **Then** `notification_send.status='RETRY'` + `retry_count=1`, 5분 후 1차 재시도. 30분 후 2차, 4시간 후 3차. 3회 모두 실패 시 status='DEAD_LETTER' + audit_log severity=CRITICAL.

### AC-POLICY-028 옵트아웃 이중 검증 (대상 추출 시점)

- **Given** 사용자가 대상 추출 직전 `notification_subscription.opted_in=FALSE` 변경
- **When** 대상 추출 쿼리 실행
- **Then** 해당 사용자는 `notification_dispatch_target`에 INSERT되지 않는다.

### AC-POLICY-029 옵트아웃 이중 검증 (발송 직전)

- **Given** 사용자가 대상 추출 후·발송 직전 옵트아웃 변경
- **When** 발송 직전 검증
- **Then** 발송 차단, target.status='SKIPPED_OPTOUT', notification_send 미생성.

### AC-POLICY-030 카카오 알림톡 정상 발송 + integration_log_id FK

- **Given** 카카오 알림톡 발송 성공
- **When** 발송 실행
- **Then** `notification_send` 행 생성 + SPEC-CMS-005 `integration_log` 행 적재 + `notification_send.integration_log_id`가 동일 트랜잭션에 기록된다(SPEC-CMS-004 §14.2-1 NOTE).

### AC-POLICY-031 카카오 실패 시 이메일 폴백

- **Given** 사용자 선호 채널 카카오, 카카오 발송 3회 모두 실패
- **When** 폴백 정책 활성
- **Then** 동일 dispatch_target에 대해 EMAIL 채널로 1회 추가 발송 시도, 새 idempotency_key=hash(prev_key+'EMAIL')로 멱등성 보장.

### AC-POLICY-032 INAPP 채널은 integration_log_id NULL

- **Given** 채널 INAPP로 발송
- **When** 발송 완료
- **Then** notification_send.integration_log_id IS NULL, v_notification_history 뷰 대상에서 자동 제외(SPEC-CMS-005 §13.3 INNER JOIN 조건 충족).

### AC-POLICY-033 발송 통계 조회

- **Given** 100,000건 발송 이력
- **When** SUPER_ADMIN이 `GET /api/v1/policy/dispatch-schedules/{uuid}/stats`
- **Then** v_notification_history 뷰 JOIN으로 도달률·실패율·클릭률을 P95 < 2초로 응답한다.

---

## F. 수신 동의 관리 (REQ-POLICY-006-D)

### AC-POLICY-034 사용자 옵트인 등록

- **Given** 인증된 사용자
- **When** `PUT /api/v1/me/notifications/preferences` 에 (channel='KAKAO', category='POLICY_MATCH', opted_in=true) 전송
- **Then** `notification_subscription` UPSERT, `source='USER'`, audit_log 적재.

### AC-POLICY-035 사용자 옵트아웃

- **Given** 사용자 기존 opted_in=true
- **When** `PUT` 로 opted_in=false 전송
- **Then** 즉시 갱신, 향후 발송 대상에서 자동 제외, 옵트아웃 일시가 audit_log에 기록.

### AC-POLICY-036 옵트아웃 이력 보관

- **Given** 사용자가 1년간 5회 동의 토글
- **When** SUPER_ADMIN이 사용자 동의 이력 조회
- **Then** 모든 변경이 audit_log에 보존되어 시간순 조회 가능(개인정보보호법 제22조의2 자기결정권 증빙).

### AC-POLICY-037 카테고리별 분리 동의

- **Given** 사용자가 POLICY_MATCH 동의, ANNOUNCEMENT 거부
- **When** ANNOUNCEMENT 발송 트리거
- **Then** 해당 사용자는 대상에서 제외, POLICY_MATCH 발송에는 정상 포함.

### AC-POLICY-038 관리자 강제 옵트아웃 (이의신청 처리)

- **Given** 사용자가 고객센터에 옵트아웃 요청 후 SUPER_ADMIN 처리
- **When** SUPER_ADMIN이 `PUT /api/v1/admin/users/{id}/notifications/preferences` 로 opted_in=false + source='ADMIN' 전송
- **Then** 사용자 동의 무관 발송 차단, audit_log에 SUPER_ADMIN 처리 이력 적재.

---

## G. 전환 추적 (REQ-POLICY-005-D 부속)

### AC-POLICY-039 알림 클릭 추적

- **Given** 사용자가 카카오 알림톡 링크 클릭
- **When** `POST /api/v1/policy/{id}/track` (action='CLICK_APPLY', notification_send_id=X) 호출
- **Then** `policy_application_log` 행 생성, `notification_send` 와 매핑 보관, 매칭 결과 viewed_at 갱신.

### AC-POLICY-040 정책 외부 신청 페이지 이동 추적

- **Given** 사용자가 정책 상세에서 '외부 사이트 신청' 버튼 클릭
- **When** track API 호출(action='EXTERNAL_REDIRECT')
- **Then** `policy_application_log` 적재 + `policy_match_score.applied_at` 갱신.

### AC-POLICY-041 KPI 대시보드 연동 (POLICY_APPLY_CVR)

- **Given** 1주일 발송 + 클릭 + 전환 이력 누적
- **When** SPEC-CMS-008 대시보드가 KPI 조회
- **Then** policy_application_log + notification_send JOIN으로 POLICY_APPLY_CVR 산출(SPEC-CMS-005 §13.3 KPI 시드 ⑥).

---

## H. 권한 매트릭스

### AC-POLICY-042 USER 역할은 매칭·옵트인만 가능

- **Given** 일반 USER 로그인
- **When** `POST /api/v1/policy/programs` (정책 생성) 호출
- **Then** 403 Forbidden, audit_log severity=WARN 적재.

### AC-POLICY-043 CONTENT_ADMIN은 정책 마스터 조회·등록만, 발송 예약 불가

- **Given** CONTENT_ADMIN 로그인
- **When** `POST /api/v1/policy/dispatch-schedules` 호출
- **Then** 403 Forbidden.

### AC-POLICY-044 SUPER_ADMIN(SYSADMIN alias 포함)만 발송 예약·취소·통계 가능

- **Given** SYSADMIN(레거시 alias) 로그인
- **When** 발송 예약 API 호출
- **Then** SUPER_ADMIN aliased 권한으로 정상 처리(SPEC-CMS-002 v0.3.2 Q-4).

---

## I. 비기능·다국어

### AC-POLICY-045 매칭 API 성능 (캐시 hit)

- **Given** 캐시된 사용자 매칭
- **When** API 호출
- **Then** P95 < 500ms (캐시 hit), P95 < 2초 (cold).

### AC-POLICY-046 발송 대상 추출 성능

- **Given** 10만 명 대상
- **When** 추출 쿼리 실행
- **Then** P95 < 60초 완료(PER-003 일배치 임계값 충분).

### AC-POLICY-047 동시 발송 처리량

- **Given** 1초당 발송 요청 50건 동시 발생
- **When** 발송 실행
- **Then** Bucket4j Rate Limit + ShedLock으로 처리, 큐 적체 < 5초(PER-004).

### AC-POLICY-048 다국어 정책명 표시

- **Given** 사용자 locale='en'
- **When** 정책 검색
- **Then** `policy_program.program_name_i18n` (SPEC-CMS-004 §14.4 missing_translation 패턴) 영문이 반환되며, 누락 시 한글 fallback + missing_translation 적재.

### AC-POLICY-049 KWCAG 2.2 AA — 사용자 알림 페이지

- **Given** 사용자 알림 수신 동의 페이지
- **When** axe-core 자동 검사
- **Then** Critical/Serious 0건.

---

## J. 외부 데이터 연계 + 운영

### AC-POLICY-050 카카오 비즈채널 검수 거부 폴백

- **Given** 카카오 알림톡 템플릿 검수 거부 (REJECTED)
- **When** 발송 시도
- **Then** 시스템은 자동으로 EMAIL 채널로 폴백, audit_log에 'KAKAO_TEMPLATE_REJECTED' 적재 + 운영자 알림(SPEC-CMS-004 v0.2.1 운영매뉴얼 안내 메시지 포함).

### AC-POLICY-051 외부 OpenAPI schema 변경 감지

- **Given** 외부 API 응답에 신규 필수 필드가 추가됨
- **When** 동기화 실행
- **Then** schema validator가 미매핑 필드를 audit_log severity=WARN로 적재 + import_warnings에 기록, 기존 정책은 유지된다.

### AC-POLICY-052 발송 폭주 시 우선순위 큐

- **Given** 동시에 dispatch_type='CLOSING_SOON'(높음) + 'REMINDER'(낮음) 예약 존재
- **When** 발송 실행
- **Then** CLOSING_SOON 우선 처리, REMINDER는 대기.

---

## K. 보안 + 데이터 무결성

### AC-POLICY-053 외부 API 인증 키 마스킹

- **Given** 외부 OpenAPI 호출
- **When** integration_log 적재
- **Then** API key는 SHA-256 hash된 payload_hash로만 보존, 평문 미저장.

### AC-POLICY-054 옵트아웃 누락 0건 (감사)

- **Given** 1주 발송 100,000건
- **When** 일배치 감사 쿼리: opted_in=false 사용자에게 발송된 row
- **Then** count = 0건. 발견 시 즉시 audit_log severity=CRITICAL + 운영자 알림.

### AC-POLICY-055 매칭 점수 결정성 (재현성)

- **Given** 동일 사용자 프로필 + 동일 정책 셋
- **When** 두 번 매칭 평가
- **Then** 점수와 등급이 동일(가중치 변경 없는 한). audit_log에 score_breakdown 보존.

### AC-POLICY-056 정책 클릭 audit_log 적재율

- **Given** 1주 발송 + 사용자 클릭 1만 건
- **When** 일배치 감사
- **Then** policy_application_log row 수 ≥ 발송된 알림 추적 픽셀 호출 수 - 0.1%(브라우저 차단 허용).

---

## L. 통합 시나리오

### AC-POLICY-057 종단 시나리오 — 외부 동기화부터 신청 추적까지

- **Given** 빈 데이터베이스
- **When** (1) 외부 API 동기화 → (2) 사용자 프로필 등록 → (3) 매칭 → (4) 발송 예약 → (5) 발송 → (6) 클릭 → (7) 외부 신청
- **Then** 7단계 모두 audit_log·integration_log·policy_application_log에 일관성 있게 적재되며 v_notification_history 뷰에서 발송 결과 단일 응답으로 조회 가능.

### AC-POLICY-058 마감일 임박 시나리오

- **Given** 정책 마감 7일 전
- **When** 자동 트리거
- **Then** CLOSING_SOON 발송 예약 생성 → 야간 차단 검사 → 옵트아웃 검증 → 발송 → 추적까지 무결.

### AC-POLICY-059 옵트아웃 후 재구독

- **Given** 사용자가 옵트아웃 후 30일 후 재동의
- **When** 옵트인 토글
- **Then** 재구독 즉시 발송 대상에 포함, 이전 옵트아웃 이력은 audit_log에 보존.

### AC-POLICY-060 멀티 노드 분산 발송 정합성

- **Given** 2 노드 환경
- **When** 동일 schedule_uuid 동시 처리 시도
- **Then** ShedLock에 의해 단일 노드만 발송 처리, 중복 발송 0건.

### AC-POLICY-061 정책 신청 전환 후 매칭 재계산

- **Given** 사용자가 정책 신청 완료(`applied_at` 갱신)
- **When** 다음 매칭
- **Then** 신청 완료된 정책은 매칭 결과에서 제외(중복 추천 방지) + audit_log 적재.

### AC-POLICY-062 발송 통계 정확도 (v_notification_history 뷰)

- **Given** 1만 건 카카오 발송 + 일부 INAPP 발송
- **When** v_notification_history 조회
- **Then** INAPP는 자동 제외(integration_log_id NULL), KAKAO/MAIL만 INNER JOIN되어 도달률·실패율 정확히 산출.

### AC-POLICY-063 다국어 매칭 사유

- **Given** 사용자 locale='en' + 매칭 사유 조회
- **When** `GET /api/v1/policy/match/{id}/reason`
- **Then** score_breakdown.dimension_labels가 영문(industry/region/size/age/revenue) + 룰 description_en 반환, 누락 시 ko fallback + missing_translation 적재.

---

## M. 추적 + 분석 KPI

### AC-POLICY-064 KPI ⑥ 정책매칭 신청 전환율

- **Given** 1주 매칭 5,000건 + 신청 500건
- **When** SPEC-CMS-008 대시보드에서 POLICY_APPLY_CVR 조회
- **Then** 500/5000 = 10% 정확 산출.

### AC-POLICY-065 KPI ⑦ 알림 도달률

- **Given** 카카오 발송 1만 건 (성공 9,500 + 실패 500)
- **When** NOTI_DELIVERY_RATE 조회
- **Then** v_notification_history에서 9,500/10,000 = 95% 산출.

---

## 5 Quality Gates

| QG | 명칭 | 게이트 기준 | 확인 방법 |
|---|---|---|---|
| **QG-POLICY-1** | 보안 | 외부 OpenAPI 인증키 평문 노출 0건, 옵트아웃 강제 적용 | secret scan + AC-POLICY-053/054 |
| **QG-POLICY-2** | 성능 | 매칭 API P95 < 500ms (cache hit) / < 2초 (cold), 발송 추출 P95 < 60초 (10만 명) | 부하 테스트 (k6) — AC-POLICY-045/046/047 |
| **QG-POLICY-3** | 정확도 | 자격요건 평가 100%, 매칭 score 결정성, EXCLUDE 우선 적용 | 단위·통합 테스트 — AC-POLICY-009/011~015/055 |
| **QG-POLICY-4** | 발송 무결성 | 멱등성 100%, 옵트아웃 누락 0건, 야간 차단 100%, integration_log_id FK 정합성 100% | 통합 테스트 + 일배치 감사 — AC-POLICY-025/026/028/029/030/032/054/060 |
| **QG-POLICY-5** | 추적 | 클릭/전환 audit_log 적재율 ≥ 99.9%, KPI ⑥/⑦ 정확도 ±1% | 통합 테스트 + 시계열 검증 — AC-POLICY-039~041/056/064/065 |

---

## Definition of Done

- [ ] 65개 acceptance entry 모두 통과 (AC-POLICY-001~065)
- [ ] 5 QG 모두 PASS
- [ ] JaCoCo 단위 + 통합 테스트 커버리지 ≥ 85% (SPEC-CMS-001 §9 비기능)
- [ ] axe-core KWCAG 2.2 AA 위반 0건
- [ ] SPEC-CMS-004 `notification_send` + SPEC-CMS-005 `v_notification_history` 뷰 통합 테스트 통과
- [ ] 카카오 알림톡 운영매뉴얼(`docs/operations/kakao-template.md`) 정책 매칭 카테고리 템플릿 검수 신청 완료
- [ ] 6 parent REQ × 28 sub-REQ 모두 acceptance.md에 매핑 추적 완료

---

Version: v0.1
Last Updated: 2026-04-29
Author: manager-spec (MoAI)
Coverage: 65 G/W/T entries × 5 Quality Gates
