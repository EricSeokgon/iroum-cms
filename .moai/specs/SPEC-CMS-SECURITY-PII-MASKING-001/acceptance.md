# SPEC-CMS-SECURITY-PII-MASKING-001 — Acceptance Criteria v0.1

본 문서는 SPEC-CMS-SECURITY-PII-MASKING-001 (PII 운영 노출 통제 — Logback 정규식 마스킹 + MDC PII 필드 정책 + JWT 로그 마스킹)의 수락 기준을 정의한다. 모든 AC는 Given-When-Then 형식으로 기술되며, 각 REQ는 최소 1개의 AC와 매핑된다.

---

## A. REQ-PII-MASK-001 — Logback 정규식 마스킹 4종

### AC-MASK-001-1 — email 마스킹 적용

- **Given**: 서비스 코드에서 `log.info("사용자: {}", "user@example.com")` 호출
- **When**: 운영 prod 프로파일 LogstashEncoder 또는 dev `!prod` 프로파일 ConsoleAppender로 로그 출력
- **Then**:
  - 출력된 로그 메시지에 평문 `user@example.com` 미포함
  - 출력에 마스킹 문자열(`***@***` 또는 동등) 포함
  - 로그 레벨/타임스탬프/MDC 등 다른 필드는 정상 출력

### AC-MASK-001-2 — 국내 phone 마스킹 적용

- **Given**: `log.info("연락처: {}", "010-1234-5678")` 호출
- **When**: 모든 프로파일에서 로그 출력
- **Then**:
  - 평문 `010-1234-5678` 미포함
  - 마스킹된 형태(`01*-****-****` 또는 동등) 포함
  - 다른 패턴(011/016/017/018/019)도 동일하게 마스킹

### AC-MASK-001-3 — SSN(주민등록번호) 마스킹 적용

- **Given**: `log.info("주민번호: {}", "990101-1234567")` 호출
- **When**: 모든 프로파일에서 로그 출력
- **Then**:
  - 평문 `990101-1234567` 미포함
  - 뒷자리 7자(또는 전체) 마스킹 (`******-*******` 또는 동등)
  - 하이픈 없는 형식(`9901011234567`)도 패턴 매칭 시 마스킹

### AC-MASK-001-4 — IPv4 마스킹 적용

- **Given**: `log.info("IP: {}", "192.168.1.100")` 호출
- **When**: 모든 프로파일에서 로그 출력
- **Then**:
  - 평문 `192.168.1.100` 미포함 (단, MDC `clientIp`/`ipAddress`는 REQ-PII-MASK-002로 별도 SHA-256 prefix 적용)
  - 마스킹된 형태(`***.***.***.***` 또는 부분 마스킹) 포함

### AC-MASK-001-5 — false positive 미발생 (일반 텍스트)

- **Given**: `log.info("hello world {}", "정상 메시지입니다")` 호출
- **When**: 모든 프로파일에서 로그 출력
- **Then**:
  - 일반 텍스트 변경 없음 (정규식 4종 미매칭)
  - 한글/영문 단어/숫자 단독은 마스킹 대상 아님 (예: "user", "100", "테스트")

### AC-MASK-001-6 — 운영 prod LogstashEncoder + 마스킹 호환성

- **Given**: prod 프로파일 활성화 + `log.info("user: user@example.com")` 호출
- **When**: LogstashEncoder가 JSON 직렬화
- **Then**:
  - JSON 출력의 `message` 필드에 마스킹 적용 (`"message":"user: ***@***"`)
  - JSON 구조 유효 (`@version`, `traceId`, `spanId` 등 다른 필드 정상 포함)
  - JSON 파싱 가능 (운영 로그 수집 시스템 호환)

---

## B. REQ-PII-MASK-002 — MDC PII 필드 SHA-256 prefix

### AC-MASK-002-1 — clientIp SHA-256 prefix 8자 적용 (MdcLoggingFilter)

- **Given**: HTTP 요청에 `X-Forwarded-For: 192.168.1.100` 헤더 포함
- **When**: `MdcLoggingFilter.doFilter` 처리
- **Then**:
  - MDC `clientIp` 값은 평문 `192.168.1.100`이 아님
  - MDC `clientIp` 값은 `HashUtil.sha256Hex("192.168.1.100").substring(0, 8)` 결과와 일치 (8자 hex 문자열)
  - 예시: `192.168.1.100` → 약 `a1b2c3d4` 형태(실제 값은 SHA-256 결과에 따라 결정)
  - 응답 후 `MDC.clear()`로 정리됨

### AC-MASK-002-2 — ipAddress SHA-256 prefix 적용 (RequestContextFilter)

- **Given**: HTTP 요청에 `X-Forwarded-For: 10.0.0.5` 헤더 포함
- **When**: `RequestContextFilter.doFilterInternal` 처리
- **Then**:
  - MDC `ipAddress` 값은 평문 `10.0.0.5`이 아님
  - MDC `ipAddress` 값은 `HashUtil.sha256Hex("10.0.0.5").substring(0, 8)` 결과와 일치
  - 응답 후 `MDC.remove(MDC_IP)`로 정리됨

### AC-MASK-002-3 — 추적성 검증 (동일 IP → 동일 prefix)

- **Given**: 동일 IP `203.0.113.42`에서 두 번의 HTTP 요청
- **When**: 두 요청 모두 `MdcLoggingFilter` + `RequestContextFilter` 처리
- **Then**:
  - 두 요청의 MDC `clientIp` 값이 동일 (디버깅 추적 가능)
  - 두 요청의 MDC `ipAddress` 값이 동일
  - 다른 IP(예: `203.0.113.43`) 요청과는 prefix가 다름

### AC-MASK-002-4 — 빈 문자열/null 입력 가드

- **Given**: `X-Forwarded-For` 헤더 없고 `request.getRemoteAddr()` 결과가 빈 문자열 또는 null
- **When**: 필터 처리
- **Then**:
  - SHA-256 변환 시도 X (NPE/예외 발생 X)
  - MDC `clientIp`/`ipAddress`는 빈 문자열 또는 미설정 상태로 보존
  - 로그 출력 정상 진행

### AC-MASK-002-5 — userId/traceId/spanId/userAgent 평문 보존

- **Given**: JWT 인증 통과(userId=1) + traceId/spanId/requestId 자동 발급 + User-Agent 헤더
- **When**: 필터 처리
- **Then**:
  - MDC `userId` = "1" (평문, DB 식별자로 PII 아님)
  - MDC `traceId`/`spanId`/`requestId` = UUID 문자열 (평문, 추적 ID)
  - MDC `userAgent` = 헤더 원본 (평문, PII 아닌 식별자)
  - 위 필드는 SHA-256 변환 대상이 아님

---

## C. REQ-PII-MASK-003 — JwtAuthenticationFilter PII log 정정

### AC-MASK-003-1 — username 미출력 (userId만 로그)

- **Given**: JWT 인증 완료 (claims.userId() = 1L, claims.username() = "testuser@example.com")
- **When**: `JwtAuthenticationFilter.doFilterInternal` 내 `log.debug("JWT 인증 완료: ...")` 호출
- **Then**:
  - 로그 메시지 = "JWT 인증 완료: userId=1" 형태 (username 미포함)
  - 메시지에 "testuser@example.com" 또는 "username=" 문자열 미포함
  - userId는 DB 식별자(Long)로 정상 출력 (디버깅 추적 가능)

### AC-MASK-003-2 — 인증 동작 회귀 없음

- **Given**: 정상 JWT를 포함한 HTTP 요청
- **When**: `JwtAuthenticationFilter` 처리
- **Then**:
  - SecurityContext에 `JwtPrincipal` 정상 설정 (userId/username/roles/permissions 모두 보존)
  - filterChain 정상 진행
  - 후속 필터/컨트롤러에서 `principal.username()` 호출 시 정상 반환
  - 로그 변경이 인증 도메인 동작에 영향 없음

---

## D. Quality Gates (GREEN 조건)

### D.1 빌드 및 테스트

- **D.1.1**: `./gradlew :backend:build` 성공 (LSP 0 errors / 0 type errors / 0 lint errors)
- **D.1.2**: `./gradlew :backend:test` 전체 GREEN
  - 신규 단위 테스트 GREEN: `PiiMaskingPatternTest` (5 케이스, AC-MASK-001-1 ~ AC-MASK-001-5)
  - 신규/수정 단위 테스트 GREEN: `MdcLoggingFilterTest` (4 케이스, AC-MASK-002-1 ~ AC-MASK-002-4)
  - 신규 단위 테스트 GREEN: `JwtAuthenticationFilterLogTest` (1 케이스, AC-MASK-003-1)
- **D.1.3**: `./gradlew :backend:integrationTest` 전체 GREEN (회귀 0건)
  - PII-001 IT 통과 (Email AES-256-GCM 회귀 없음)
  - PII-002 IT 통과 (PII 노출 통제 회귀 없음)
  - PII-FOLLOWUP-001 IT 통과 (비동기 감사 IT 회귀 없음)
  - JWT 인증 IT 통과 (로그 변경 회귀 없음)

### D.2 회귀 없음 (Negative Confirmation)

- **D.2.1**: 변경 4 파일 외 운영 코드 수정 없음 (Diff 검증)
- **D.2.2**: 신규 DDL 없음 (`db/migration/` 신규 파일 없음)
- **D.2.3**: 신규 외부 의존성 없음 (`build.gradle.kts` 변경 없음 — 자체 정규식 채택, D4-(a))
- **D.2.4**: 운영 prod JSON 인코더 호환성 (LogstashEncoder + 마스킹 결합 시 JSON 유효성 보존, AC-MASK-001-6)

### D.3 마스킹 효과 검증 (운영 시뮬레이션)

- **D.3.1**: 통합 검증 시나리오 — 운영 환경 모사 로그 출력에서 PII 노출 0건
  - 인증 요청 → JwtAuthenticationFilter:116 로그 출력에 username 평문 0건
  - HTTP 요청 → JSON 로그의 `clientIp`/`ipAddress` 필드에 IP 평문 0건 (SHA-256 prefix 적용)
  - 서비스 레이어 가상 PII log 호출 → 마스킹 정규식 4종 모두 적용

### D.4 모든 프로파일 적용 확인

- **D.4.1**: prod 프로파일 — LogstashEncoder + 마스킹 동시 적용 확인
- **D.4.2**: local/integration 프로파일 — ConsoleAppender + 마스킹 동시 적용 확인
- **D.4.3**: 프로파일별 정규식 분기 없음 (단일 정규식 정의)

---

## E. Definition of Done (완료 정의)

본 SPEC은 다음 모든 조건이 충족되면 "Implemented"로 전환된다.

### E.1 잔여 갭 3건 모두 해소

- [ ] **갭 1 — Logback 정규식 마스킹 도입**: `logback-spring.xml`에 PII 패턴 4종(email/phone/SSN/IPv4) 정규식 마스킹 적용 (REQ-PII-MASK-001)
- [ ] **갭 2 — MDC PII 필드 SHA-256 prefix**: `MdcLoggingFilter` + `RequestContextFilter`의 `clientIp`/`ipAddress` 필드 SHA-256 hex prefix 8자로 변환 (REQ-PII-MASK-002)
- [ ] **갭 3 — JwtAuthenticationFilter PII log 정정**: line 116에서 `username` 출력 제거, `userId`만 로그 (REQ-PII-MASK-003)

### E.2 운영 코드 변경 4 파일

- [ ] `backend/src/main/resources/logback-spring.xml` — Step 1
- [ ] `backend/src/main/java/kr/co/ircp/cms/common/log/MdcLoggingFilter.java` — Step 2
- [ ] `backend/src/main/java/kr/co/ircp/cms/config/RequestContextFilter.java` — Step 2
- [ ] `backend/src/main/java/kr/co/ircp/cms/security/JwtAuthenticationFilter.java` — Step 3

### E.3 모든 프로파일 적용

- [ ] prod (LogstashEncoder) + local/integration (ConsoleAppender) 모두 마스킹 적용 (D4-(d))
- [ ] 단일 정규식 정의 (프로파일별 분기 없음)

### E.4 테스트 GREEN

- [ ] 단위 테스트 10건 GREEN (AC-MASK-001-1~6 + AC-MASK-002-1~5 + AC-MASK-003-1~2 중 단위 테스트 가능 항목)
- [ ] 회귀 IT 0건 (PII-001/002/FOLLOWUP-001 + JWT IT 통과)
- [ ] LSP 0 errors / 0 type errors / 0 lint errors

### E.5 신규 의존성/DDL 없음

- [ ] 신규 외부 라이브러리 의존성 추가 없음 (`build.gradle.kts` 변경 없음)
- [ ] 신규 DDL 없음 (`db/migration/` 변경 없음)
- [ ] 운영 환경(prod) 동작 변경 검증 — JSON 로그 유효성 + 마스킹 적용 동시 확인

### E.6 후속 SPEC 안내 (비범위 명시)

- [ ] spec.md §10 후속 SPEC 안내 명시 완료:
  - SPEC-CMS-SECURITY-PII-BACKUP-001 (pg_dump 백업 PII — 운영팀 영역)
  - SPEC-CMS-SECURITY-PII-LOG-AUDIT-001 (전 코드베이스 PII log audit)
- [ ] 본 SPEC 비범위 항목(§3.2)이 후속 SPEC으로 추적 가능

---

## F. 리뷰 체크리스트 (commit 전 검토)

- [ ] 변경 4 파일 모두 Diff 확인 (운영 코드 변경 최소화 원칙)
- [ ] `logback-spring.xml` 변경이 prod + dev 모두 적용됨
- [ ] MDC `clientIp`/`ipAddress` 변경이 AuditLogAspect 등 의존 컴포넌트에 영향 없음 확인
- [ ] JwtAuthenticationFilter:116 변경이 인증 동작에 영향 없음 (단위 테스트 + IT 검증)
- [ ] 단위 테스트 의도가 명확 (각 AC 매핑)
- [ ] 회귀 IT 통과 (기존 PII 트랙 SPEC 동작 보존)
- [ ] commit 메시지에 SPEC ID 포함 (`feat(security): SPEC-CMS-SECURITY-PII-MASKING-001 ...`)
