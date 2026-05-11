# SPEC-CMS-SECURITY-PII-MASKING-001: PII 운영 노출 통제 (Logback 마스킹 + MDC 정책 + JWT log 마스킹) v0.2

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-SECURITY-PII-MASKING-001 |
| 제목 | PII 운영 노출 통제 (Logback 정규식 마스킹 + MDC PII 필드 정책 + JWT 로그 마스킹) |
| 작성일 | 2026-05-11 |
| 작성자 | manager-spec (MoAI) |
| 상태 | Implemented (1차 — Step 1~4 완료, 2026-05-11) |
| 우선순위 | **P1 (운영 노출 위험 통제)** |
| 분류 | Cross-cutting Security Operational Procedure SPEC |
| 의존 SPEC | SPEC-CMS-SECURITY-PII-001 §3.2 비범위 일부 이행, SPEC-CMS-005 (Logback/MDC 인프라) |
| 형제 SPEC | SPEC-CMS-SECURITY-PII-001 (Email AES-256-GCM 암호화 — Implemented), SPEC-CMS-SECURITY-PII-002 (PII 노출 통제 — Implemented), SPEC-CMS-SECURITY-PII-FOLLOWUP-001 (PII 비동기 감사 IT 인프라 — Implemented) |

본 SPEC은 PII 트랙의 4번째 SPEC으로, SPEC-CMS-SECURITY-PII-001 §3.2 비범위 항목 중 "로그 중 PII 마스킹 (Logback PatternLayout + 정규식 마스킹 필터 도입은 운영 표준 영역)"의 부분 이행을 담당한다. 운영 환경에서 PII가 평문으로 로그에 적재되어 ELK/Loki 등 로그 수집 시스템에 PII가 영속화되는 위험을 application 레벨에서 차단하는 것이 목적이다. 백업 파일 PII 마스킹(pg_dump)은 운영 백업 정책 영역으로, 본 SPEC의 비범위로 명시한다(별도 후속 SPEC으로 분리).

**구현 대상 요구사항**: REQ-PII-MASK-001 (Logback 정규식 마스킹 4종), REQ-PII-MASK-002 (MDC PII 필드 SHA-256 prefix 적용), REQ-PII-MASK-003 (JwtAuthenticationFilter PII log 정정).

본 SPEC의 1차 범위는 (1) `logback-spring.xml`에 자체 정규식 기반 마스킹 패턴 4종(email/phone/SSN/IPv4)을 모든 프로파일(prod JSON + dev pattern)에 적용하고, (2) `MdcLoggingFilter`/`RequestContextFilter`의 `clientIp`/`ipAddress` 필드를 `HashUtil.sha256Hex` prefix 8자(hex)로 변환하여 디버깅 추적성을 유지하면서 PII 노출을 차단하며, (3) `JwtAuthenticationFilter:116`의 `log.debug("JWT 인증 완료: userId={}, username={}", ...)` 라인에서 `username` 필드를 제거하여 DEBUG 활성화 시에도 PII 노출이 발생하지 않도록 정정하는 것이다. 운영 코드 변경 4 파일에 한정되며, 신규 DDL은 없다.

---

## 2. 배경 및 동기

### 2.1 PII-001 §3.2 비범위 후속 — 본 SPEC의 위치

SPEC-CMS-SECURITY-PII-001 §3.2 (비범위)에는 다음 두 항목이 명시되어 있다.

> | 백업 파일 PII 마스킹 | 운영 백업 정책 영역. 별도 운영 절차 정의. |
> | 로그 중 PII 마스킹 (Logback 필터) | 별도 작업. Logback PatternLayout + 정규식 마스킹 필터 도입은 운영 표준 영역. 후속 SPEC(SPEC-CMS-SECURITY-PII-MASKING-001). |

본 SPEC은 위 두 항목 중 **"로그 중 PII 마스킹 (Logback 필터)"** 영역만 처리한다. 백업 파일 PII 마스킹(pg_dump 단계 마스킹)은 본 코드베이스에 백업 스크립트가 존재하지 않으며 운영팀의 인프라/백업 정책 영역이므로, 본 SPEC의 비범위(§3.2)로 분리하여 별도 후속 SPEC(`SPEC-CMS-SECURITY-PII-BACKUP-001`(가칭))에서 처리한다.

### 2.2 MoAI 정밀 진단 — 이미 적용된 부분 vs 잔여 갭

PII-001 §3.2 "로그 마스킹" 영역에 대해 현 코드베이스를 정밀 진단한 결과, 다음과 같이 일부는 이미 적용되어 있고 일부는 잔여 갭으로 남아 있다.

#### 2.2.1 이미 적용된 부분 (4건 — 본 SPEC 범위 외)

| # | 항목 | 위치 | 비고 |
|---|------|------|------|
| 1 | 운영 prod 프로파일 JSON 구조화 인코더 | `logback-spring.xml` `<springProfile name="prod">` | LogstashEncoder + customFields {"@version":"1"} |
| 2 | MDC 표준 필드 정의 (5종) | `MdcLoggingFilter.java` (traceId/spanId/userId/requestId/clientIp) | SPEC-CMS-005 REQ-CROSS-007-D-2 |
| 3 | RequestContextFilter MDC 보강 (3종) | `RequestContextFilter.java` (ipAddress/userAgent/traceId) | AuditLogAspect가 참조 |
| 4 | 운영 INFO 레벨 (DEBUG 출력 차단) | `application-prod.yml` `kr.co.ircp.cms: INFO` | DEBUG 미활성 시 username/clientIp 등 노출 X |

#### 2.2.2 진정한 잔여 갭 (3건 — 본 SPEC 범위)

| # | 영역 | 현재 상태 | 잔여 갭 |
|---|------|----------|---------|
| 1 | **Logback 정규식 마스킹** | LogstashEncoder는 마스킹 미지원, 마스킹 패턴/필터 0건 | 자체 정규식 또는 라이브러리 기반 마스킹 도입 (REQ-PII-MASK-001) |
| 2 | **MDC PII 필드 정책** | `clientIp`/`userId`/`ipAddress`/`userAgent` 등 MDC가 운영 JSON 로그에 평문 포함 | `clientIp`/`ipAddress` SHA-256 prefix 마스킹/해시 정책 결정 (REQ-PII-MASK-002) |
| 3 | **JwtAuthenticationFilter:116 PII log** | `log.debug("JWT 인증 완료: userId={}, username={}", ...)` — 운영 INFO 레벨로 비노출되지만 DEBUG 일시 활성화 시 username PII 평문 출력 위험 | username 출력 정정 (제거) (REQ-PII-MASK-003) |

#### 2.2.3 비범위 (1건 — 본 SPEC 비범위)

| 영역 | 사유 |
|------|------|
| pg_dump 백업 PII 마스킹 | 본 코드베이스에 백업 스크립트가 없음 (운영팀 영역). 별도 후속 SPEC 또는 운영 가이드 문서로 분리 |

### 2.3 운영 위험 시나리오 — 본 SPEC이 차단하는 위험

본 SPEC이 해소하는 운영 위험은 다음 3가지이다.

- **시나리오 A — DEBUG 일시 활성화 시 username 평문 노출**: 운영 장애 조사를 위해 `kr.co.ircp.cms` 로거 레벨을 DEBUG로 변경하면, JwtAuthenticationFilter:116의 `log.debug("JWT 인증 완료: userId={}, username={}", ...)`이 활성화되어 모든 인증 요청마다 username 평문이 운영 로그에 영속화된다. username은 본 시스템에서 PII에 해당(이메일 또는 식별 가능한 사용자명)하므로 노출 차단이 필요하다.
- **시나리오 B — MDC clientIp/ipAddress 평문 영속화**: 운영 prod 프로파일의 LogstashEncoder는 `<includeMdcKeyName>clientIp</includeMdcKeyName>`로 MDC를 JSON 최상위에 평문 포함한다. 모든 요청 로그에 IP가 평문으로 기록되어 ELK/Loki 등 로그 수집 시스템에 PII가 장기 영속화된다(GDPR/개인정보보호법 관점에서 IP는 식별 가능 정보).
- **시나리오 C — 서비스 레이어 무심한 PII 로그**: 서비스 레이어에서 무심코 `log.info("user: {}", user)` 호출 시 `user.toString()`에 email/phone 등 PII가 포함될 수 있다. 자체 정규식 마스킹 도입으로 일관된 안전망을 구축한다.

### 2.4 결정 배경 — 사용자 사전 확정 사항

본 SPEC은 다음 사용자 결정을 사전 확정으로 채택하여 작성되었다.

| 결정 ID | 항목 | 채택 |
|---------|------|------|
| **D1** | SPEC 범위 | Logback 마스킹 + MDC 정책 + JWT log 마스킹 통합 (3건). pg_dump 백업 절차는 비범위 |
| **D2** | 우선순위 | **P1** — 운영 노출 위험 통제 |
| **D3** | KMS 의사결정 독립성 | 본 SPEC은 application 레벨 마스킹만 — KMS 의사결정 독립 수행 |

추가 결정 포인트(D4-(a)~(d))는 §6 결정 포인트에 manager-spec 권장안과 함께 명시한다.

---

## 3. 범위 및 비범위

### 3.1 범위 (P1, 본 SPEC에서 처리)

| ID | 항목 | 설명 |
|----|------|------|
| REQ-PII-MASK-001 | Logback 정규식 마스킹 4종 | `logback-spring.xml`에 정규식 마스킹 패턴 추가 (email/phone/SSN/IPv4). 모든 프로파일 적용 (prod LogstashEncoder + dev ConsoleAppender) |
| REQ-PII-MASK-002 | MDC PII 필드 SHA-256 prefix | `MdcLoggingFilter`/`RequestContextFilter`의 `clientIp`/`ipAddress` 필드 → `HashUtil.sha256Hex(ip).substring(0, 8)` 변환 |
| REQ-PII-MASK-003 | JWT log username 제거 | `JwtAuthenticationFilter:116` `log.debug("JWT 인증 완료: userId={}, username={}", ...)` → `log.debug("JWT 인증 완료: userId={}", ...)` |
| 패턴 정의 | PII 정규식 4종 | email RFC 5321 패턴, phone E.164 + 국내 010-XXXX-XXXX, SSN 주민등록번호 13자, IPv4 (IPv6는 1차 비범위) |
| 적용 범위 | 모든 프로파일 | prod + local + integration (개발 환경 PII 보호 + 운영 일관성) |

### 3.2 비범위 (별도 SPEC 또는 운영 절차 영역)

| 비범위 항목 | 사유 / 후속 처리 |
|-------------|-----------------|
| pg_dump 백업 파일 PII 마스킹 | 본 코드베이스에 백업 스크립트 부재. 운영 백업 정책 영역. 별도 가이드 문서 또는 `SPEC-CMS-SECURITY-PII-BACKUP-001`(가칭)으로 분리 |
| 컨트롤러/서비스 PII log 전수 audit | 본 SPEC은 진단으로 발견된 1건(JwtAuthenticationFilter:116)만 정정. 전수 조사 + 정정은 별도 후속 SPEC(`SPEC-CMS-SECURITY-PII-LOG-AUDIT-001`(가칭)) 또는 정기 audit 절차 |
| 로그 수집 시스템(ELK/Loki) 측 마스킹 | 인프라 측 영역. 본 SPEC은 application 레벨만 |
| ArchUnit 마스킹 강제 | PII-002의 ArchUnit 패턴은 DTO email 필드 한정. `log.*` 호출에 PII 인자 차단 강제는 별도 후속 |
| IPv6 마스킹 정규식 | 국내 시스템 IPv4 위주 환경. IPv6는 정규식 복잡성 + 사용 빈도 고려하여 1차 비범위 (필요 시 후속 추가) |
| KMS 키 관리 의사결정 | 본 SPEC은 마스킹/해시 처리만. KMS는 SPEC-CMS-SECURITY-PII-KMS-001 등 별도 SPEC에서 |

---

## 4. 주요 인터페이스 및 데이터 흐름

본 SPEC은 application 레벨 횡단 관심사로, 다음 4개 진입점에 영향을 준다.

```
[HTTP 요청]
   │
   ├─→ RequestContextFilter (Order: HIGHEST_PRECEDENCE)
   │     - MDC.put(ipAddress, sha256Prefix8(IP))   ← REQ-PII-MASK-002
   │     - MDC.put(userAgent, header)
   │     - MDC.put(traceId, UUID)
   │
   ├─→ MdcLoggingFilter
   │     - MDC.put(clientIp, sha256Prefix8(IP))    ← REQ-PII-MASK-002
   │     - MDC.put(traceId/spanId/requestId, ...)
   │
   ├─→ JwtAuthenticationFilter
   │     - log.debug("JWT 인증 완료: userId={}", ...)  ← REQ-PII-MASK-003 (username 제거)
   │
   └─→ Controller / Service (모든 log.* 호출)
         └─→ Logback Appender
               - LogstashEncoder (prod) + ConsoleAppender (dev)
               - ★ MaskingPatternLayout (자체 PatternLayout 또는 Encoder wrapper)  ← REQ-PII-MASK-001
                 ・ email: \w+@\w+\.\w+ → ***@***
                 ・ phone: 01\d-\d{4}-\d{4} → 01*-****-****
                 ・ SSN: \d{6}-\d{7} → ******-*******
                 ・ IPv4: \d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3} → ***.***.***.***
```

---

## 5. EARS 요구사항

### 5.1 REQ-PII-MASK-001 (Ubiquitous) — Logback 정규식 마스킹 4종

> **The system SHALL apply regex-based masking to all log output via Logback configuration to prevent PII (email, phone, SSN, IPv4) from being persisted in plaintext.**

**세부 명세:**
- `logback-spring.xml`에 마스킹 패턴 4종 정의:
  - **email**: `[\w.+-]+@[\w-]+\.[\w.-]+` → `***@***`
  - **phone (국내)**: `01[016789]-?\d{3,4}-?\d{4}` → `01*-****-****`
  - **SSN (주민등록번호)**: `\d{6}-?[1-4]\d{6}` → `******-*******`
  - **IPv4**: `\b(\d{1,3}\.){3}\d{1,3}\b` → `***.***.***.***` (단, 0.0.0.0 / 127.0.0.1 등 특수 주소 예외 처리는 옵션)
- 적용 방식: 자체 정규식 PatternLayout 또는 Encoder wrapper (자체 정규식 채택 — D4-(a) 권장)
- 적용 프로파일: prod LogstashEncoder + dev ConsoleAppender (모든 프로파일 — D4-(d))
- false positive 최소화: 패턴 4종에 한정하여 일반 텍스트 매칭 회피

### 5.2 REQ-PII-MASK-002 (Ubiquitous) — MDC PII 필드 SHA-256 prefix

> **The system SHALL hash MDC PII fields (clientIp, ipAddress) using SHA-256 prefix (8 chars hex) to enable debugging traceability while preventing PII exposure in JSON-encoded log output.**

**세부 명세:**
- `MdcLoggingFilter.resolveClientIp(req)` 결과에 `HashUtil.sha256Hex(ip).substring(0, 8)` 적용 후 `MDC.put(MDC_CLIENT_IP, hashedPrefix)`
- `RequestContextFilter.extractClientIp(request)` 결과에 동일 처리 후 `MDC.put(MDC_IP, hashedPrefix)`
- `userId`/`traceId`/`spanId`/`requestId`/`userAgent`는 평문 보존 (PII 아님 또는 추적 ID)
- 빈 문자열/null 입력은 그대로 보존 (해시 변환 X)
- 추적성 보장: 동일 IP 입력 시 항상 동일 prefix → 단일 사용자 행동 추적 가능

### 5.3 REQ-PII-MASK-003 (Event-driven) — JwtAuthenticationFilter PII log 정정

> **WHEN a JWT authentication completes, THEN the system SHALL log only userId (omit username) to minimize PII exposure in DEBUG-level logs.**

**세부 명세:**
- `JwtAuthenticationFilter.java:116` 변경:
  - 변경 전: `log.debug("JWT 인증 완료: userId={}, username={}", claims.userId(), claims.username());`
  - 변경 후: `log.debug("JWT 인증 완료: userId={}", claims.userId());`
- 변경 이유: 운영 INFO 레벨에서는 미노출되지만, DEBUG 일시 활성화 시 username 평문이 모든 인증 요청 로그에 적재되어 PII 영속화 위험
- 추적 식별자: userId(Long)는 DB 식별자로 PII 아님, 운영 디버깅 충분
- 운영 영향: username으로 디버깅하던 절차는 `audit_log` 테이블에서 userId 기반 조회로 대체 (이미 SPEC-CMS-005 §7 AuditLogAspect로 적재됨)

---

## 6. 결정 포인트 (사용자 사전 확정 — User-confirmed scope)

본 SPEC의 핵심 의사결정은 사용자 사전 결정으로 D1~D3가 확정되었으며, 추가 결정 포인트 D4-(a)~(d)는 manager-spec 권장안을 채택하여 명시한다(사용자 검토 가능).

### 6.1 확정된 결정 (D1~D3)

| 결정 ID | 결정 내용 | 채택 |
|---------|----------|------|
| D1 | SPEC 범위 — Logback 마스킹 + MDC 정책 + JWT log 마스킹 통합 (3건). pg_dump 백업 비범위 | 확정 |
| D2 | 우선순위 — P1 (운영 노출 위험 통제) | 확정 |
| D3 | KMS 의사결정 독립 — 본 SPEC은 application 레벨 마스킹만 | 확정 |

### 6.2 추가 결정 (D4 — manager-spec 권장안)

| 결정 ID | 항목 | 권장안 (채택) | 사유 |
|---------|------|--------------|------|
| **D4-(a)** | Logback 마스킹 방식 | **자체 정규식 PatternLayout/Encoder wrapper** | 외부 의존성 없음(`logstash-logback-encoder`는 이미 도입됨), 직접 제어 가능, PII 패턴 4종 한정으로 단순. `logback-pii-masker` 등 별도 라이브러리는 의존성 추가 + 유지보수 부담 |
| **D4-(b)** | MDC clientIp/ipAddress 정책 | **SHA-256 hex prefix 8자(32비트)** | 평문 보존(PII 노출) ❌ vs 완전 마스킹(추적성 상실) ❌의 양립. SHA-256 prefix는 디버깅 식별성(동일 IP → 동일 prefix) + PII 보호(역산 불가)를 모두 충족. 8자(32비트)는 IT 환경 IP 분포 가정 시 충돌 가능성 낮음(필요 시 prefix 길이 확장 가능) |
| **D4-(c)** | JwtAuthenticationFilter:116 정정 방식 | **username 제거 (userId만 출력)** | (1) PII 최소화 원칙: 디버깅 필수 정보(userId)만 보존, (2) 마스킹은 정규식 패턴이 처리하므로 중복, (3) DEBUG → TRACE 격하는 운영 조사 시 활성화 어려움 |
| **D4-(d)** | 마스킹 적용 프로파일 | **모든 프로파일 (prod + dev + integration)** | (1) 개발 환경에서도 PII 보호 (개발자 단말 로그 영속화 위험), (2) 운영-개발 일관성으로 회귀 검출 용이, (3) 프로파일별 정규식 분기는 유지보수 부담 |

---

## 7. 위험(RISK) 및 완화

| RISK ID | 위험 | 영향 | 완화 |
|---------|------|------|------|
| RISK-MASK-01 | 정규식 마스킹 false positive (예: 일반 텍스트가 email 패턴 매칭) | 마스킹 과다 적용으로 디버깅 가독성 저하 | 정규식 정밀화(`[\w.+-]+@[\w-]+\.[\w.-]+`) + 단위 테스트로 false positive 시나리오 검증 (예: "hello world" 미변경) |
| RISK-MASK-02 | SHA-256 hex prefix 8자(32비트) 충돌 | 운영 디버깅 식별성 약화 (서로 다른 IP가 동일 prefix) | IT 환경 IP 분포 가정 시 충돌 가능성 낮음. 운영 모니터링 후 충돌 빈도 측정 → 필요 시 prefix 길이 12자/16자로 확장 |
| RISK-MASK-03 | JWT username 제거로 인한 운영 디버깅 어려움 | username 기반 사용자 식별 불가 | userId(Long)로 `audit_log` 테이블 조회 가능 (SPEC-CMS-005 §7 AuditLogAspect로 적재). 운영 조사 절차 보강(가이드 문서) |
| RISK-MASK-04 | 마스킹 패턴 회귀 (신규 PII 컬럼/필드 추가 시 패턴 누락) | 신규 PII가 평문 노출 | 정기 audit + 후속 SPEC(`SPEC-CMS-SECURITY-PII-LOG-AUDIT-001`)에서 ArchUnit으로 `log.*` 호출 PII 인자 강제 검토 |
| RISK-MASK-05 | Logback 정규식 마스킹 성능 영향 (모든 로그 메시지에 정규식 적용) | 로그 처리량 저하 | 운영 INFO 레벨 + 패턴 4종 한정 → 영향 미미. 운영 모니터링으로 latency 측정 (목표: 마스킹 미적용 대비 +5% 미만) |
| RISK-MASK-06 | LogstashEncoder + 자체 PatternLayout 호환성 | 운영 prod JSON 인코더와 마스킹 충돌 | LogstashEncoder는 message 필드만 마스킹 적용 (MDC 필드는 REQ-PII-MASK-002에서 별도 처리). 호환성 IT로 검증 |

---

## 8. 가정(ASSUM) 및 의존성(DEPS)

### 8.1 가정 (ASSUM)

| ASSUM ID | 가정 |
|----------|------|
| ASSUM-MASK-01 | `HashUtil.sha256Hex` 인프라 가용 (PII-001에서 도입 완료, `domain/auth/util/HashUtil.java`) |
| ASSUM-MASK-02 | Logback 1.5.x + `logstash-logback-encoder` 8.x 패턴 호환 (자체 PatternLayout/Encoder wrapper 도입 가능) |
| ASSUM-MASK-03 | 운영팀이 마스킹된 로그로 디버깅 가능 (식별성은 userId/traceId/SHA-256 prefix로 유지) |
| ASSUM-MASK-04 | pg_dump 백업 영역은 별도 운영 절차로 분리 (본 SPEC 비범위) |
| ASSUM-MASK-05 | 본 SPEC은 application 레벨 마스킹만 — KMS/암호화는 PII-001(완료) + 별도 KMS SPEC 영역 |
| ASSUM-MASK-06 | SHA-256 hex prefix 8자(32비트)는 본 시스템 IP 트래픽 분포에서 충분한 식별성 제공 |

### 8.2 의존성 (DEPS)

**대상 파일 (운영 코드 변경 4 파일):**
- `backend/src/main/resources/logback-spring.xml` — REQ-PII-MASK-001 (마스킹 패턴 4종 + 모든 프로파일)
- `backend/src/main/java/kr/co/ircp/cms/common/log/MdcLoggingFilter.java` — REQ-PII-MASK-002 (clientIp SHA-256 prefix)
- `backend/src/main/java/kr/co/ircp/cms/config/RequestContextFilter.java` — REQ-PII-MASK-002 (ipAddress SHA-256 prefix)
- `backend/src/main/java/kr/co/ircp/cms/security/JwtAuthenticationFilter.java` — REQ-PII-MASK-003 (line 116 username 제거)

**재사용 인프라:**
- `backend/src/main/java/kr/co/ircp/cms/domain/auth/util/HashUtil.java` — PII-001 인프라 재사용 (변경 없음)

**테스트 파일 (신규/수정):**
- `backend/src/test/java/kr/co/ircp/cms/common/log/PiiMaskingPatternTest.java` (신규) — REQ-PII-MASK-001 마스킹 패턴 4종 단위 테스트
- `backend/src/test/java/kr/co/ircp/cms/common/log/MdcLoggingFilterTest.java` (수정 또는 신규) — REQ-PII-MASK-002 SHA-256 prefix 검증
- `backend/src/test/java/kr/co/ircp/cms/security/JwtAuthenticationFilterLogTest.java` (신규 또는 기존 IT 보강) — REQ-PII-MASK-003 로그 출력 검증

**의존 SPEC:**
- SPEC-CMS-SECURITY-PII-001 (Email AES-256-GCM — Implemented, HashUtil 인프라 제공)
- SPEC-CMS-005 (Logback 운영 인코더 + MDC 표준 필드 — Implemented)

---

## 9. RUN 단계 분해 (Step 1~4)

본 SPEC의 RUN 단계는 4개 Step으로 분해된다. 각 Step은 독립 commit 단위로 진행하며, Step 간 회귀를 방지한다.

### 9.1 Step 1 — Logback 정규식 마스킹 도입 (REQ-PII-MASK-001)

**범위:**
- `logback-spring.xml`에 자체 정규식 PatternLayout 또는 Encoder wrapper 추가
- PII 패턴 4종 정의 (email/phone/SSN/IPv4)
- prod LogstashEncoder + dev ConsoleAppender 모두 적용 (D4-(d))
- 단위 테스트: `PiiMaskingPatternTest.java` — 마스킹 패턴 4종 매칭 + false positive 미발생 (5개 테스트 케이스 — AC-MASK-001-1 ~ AC-MASK-001-5)

**검증:**
- 단위 테스트 GREEN
- 회귀: 기존 IT 통과 (로그 포맷 변경이 IT 단언에 영향 없음 확인)

### 9.2 Step 2 — MDC PII 필드 SHA-256 prefix 적용 (REQ-PII-MASK-002)

**범위:**
- `MdcLoggingFilter.java` 수정: `MDC.put(MDC_CLIENT_IP, ...)` 라인에 `HashUtil.sha256Hex(ip).substring(0, 8)` 적용
- `RequestContextFilter.java` 수정: `MDC.put(MDC_IP, ...)` 라인에 동일 처리
- 빈 문자열/null 입력 가드 (해시 변환 X, 그대로 보존)
- 단위 테스트: SHA-256 prefix 정확성 + 추적성(동일 IP → 동일 prefix) + null/empty 가드 (3개 테스트 케이스 — AC-MASK-002-1 ~ AC-MASK-002-3)

**검증:**
- 단위 테스트 GREEN
- 회귀: AuditLogAspect가 MDC `ipAddress`를 참조하므로, 해시된 prefix가 audit_log에 적재되는지 IT로 검증 (또는 audit_log 컬럼 마스킹 정책 명시)

### 9.3 Step 3 — JwtAuthenticationFilter PII log 정정 (REQ-PII-MASK-003)

**범위:**
- `JwtAuthenticationFilter.java:116` 수정:
  - `log.debug("JWT 인증 완료: userId={}, username={}", claims.userId(), claims.username());` → `log.debug("JWT 인증 완료: userId={}", claims.userId());`
- 단위 테스트: 로그 출력 검증 — username 미포함 (1개 테스트 케이스 — AC-MASK-003-1)

**검증:**
- 단위 테스트 GREEN
- 회귀: JwtAuthenticationFilter IT 통과 (로그 변경이 인증 동작에 영향 없음)

### 9.4 Step 4 — 통합 검증 및 회귀

**범위:**
- 모든 변경(4 파일) 적용 후 backend 전체 빌드 + 단위 테스트 + IT
- 마스킹 효과 시뮬레이션: 운영 환경 모사 로그 출력으로 PII 노출 0건 확인
- 운영 가이드 문서 (필요 시): "username 기반 디버깅 → userId + audit_log 조회 절차"

**검증:**
- 전체 GREEN (단위 테스트 + IT)
- LSP 0 errors / 0 type errors / 0 lint errors
- 회귀 0건 (기존 PII-001/002/FOLLOWUP-001 IT 통과)

---

## 10. 후속 SPEC 안내

본 SPEC 완료 후 다음 후속 SPEC이 PII 트랙에 추가될 수 있다(우선순위 별도 결정).

| 후속 SPEC (가칭) | 영역 | 우선순위 |
|------------------|------|---------|
| SPEC-CMS-SECURITY-PII-BACKUP-001 | pg_dump 백업 PII 마스킹 절차 (운영 백업 정책) | P2 (운영팀 협의 필요) |
| SPEC-CMS-SECURITY-PII-LOG-AUDIT-001 | 전 코드베이스 `log.*` PII 출현 audit + ArchUnit 강제 | P2 (정기 audit 영역) |
| SPEC-CMS-SECURITY-PII-KMS-001 | AES-256-GCM 키 관리 KMS 마이그레이션 | (KMS 의사결정 후) |
| SPEC-CMS-SECURITY-PII-ROTATION-001 | 암호화 키 rotation 절차 | (KMS 의사결정 후) |

---

## 11. 변경 이력

- **v0.1 (2026-05-11)**: 초안 작성. SPEC-CMS-SECURITY-PII-001 §3.2 비범위 항목 중 "로그 마스킹" 영역 후속 SPEC. MoAI 정밀 진단으로 4건 갭 식별(이미 적용 4건 + 잔여 갭 3건 + 비범위 1건). 사용자 결정 D1~D3 확정 + manager-spec 권장안 D4-(a)~(d) 채택. REQ-PII-MASK-001/002/003 정의. RUN Step 1~4 분해. 운영 코드 변경 4 파일(logback-spring.xml + MdcLoggingFilter + RequestContextFilter + JwtAuthenticationFilter), 신규 DDL 없음. 백업 절차 비범위 명시.
- **v0.2 (2026-05-11)**: RUN 1차 완료 — 9 파일 +575/-10 (commit `bfd7488`). REQ-PII-MASK-001 (Logback 마스킹 모든 프로파일, `logstash-logback-encoder 7.4` `MaskingJsonGeneratorDecorator` + 자체 `PiiMaskingConverter`) + REQ-PII-MASK-002 (MDC `clientIp`/`ip` SHA-256 prefix 8자) + REQ-PII-MASK-003 (JWT log username 제거). 신규 테스트 3 파일 (`LogbackPiiMaskingTest` 140줄 12 메서드 + `MdcSha256MaskingTest` 132줄 4 메서드 + `JwtAuthLogTest` 131줄) + `MdcLoggingFilterTest` 회귀 정정. 사용자 결정 D1+D4-(a)~(d) 모두 채택. PIPA 제29조 추가 완화. | manager-docs
