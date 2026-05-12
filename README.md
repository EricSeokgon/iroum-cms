# iroum-cms — 공공기관 CMS

전자정부 표준프레임워크(egovframe) 5.0 기반 공공기관 콘텐츠 관리 시스템.

- 백엔드: Spring Boot 3.2 + egovframe 5.0 + MyBatis + PostgreSQL 16
- 관리자 SPA: Vue 3 + TypeScript + Element Plus (운영자 백오피스)
- 공공 SPA: Vue 3 + TypeScript (시민 공개 웹사이트)
- 인프라: Docker Compose + nginx + GitHub Actions CI

---

## 디렉터리 구조

```
iroum-cms/
├── backend/                  # Spring Boot 3.2 + egovframe 5.0
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/
├── frontend/
│   ├── admin/                # 관리자 SPA (40~50 페이지)
│   ├── public/               # 공공 웹사이트 SPA (20~30 페이지)
│   └── shared/               # 공통 타입·유틸
├── deploy/
│   ├── Dockerfile.backend    # Multi-stage: JDK17 빌드 → JRE17 런타임
│   ├── Dockerfile.frontend   # Multi-stage: Node22 빌드 → nginx 서빙 (admin/public 공용)
│   ├── docker-compose.yml    # 로컬 개발 전체 스택
│   ├── nginx/
│   │   ├── nginx.conf        # 메인 nginx 설정
│   │   ├── conf.d/
│   │   │   └── default.conf  # 라우팅 (API / Admin / Public)
│   │   └── spa.conf          # SPA Vue Router 폴백 설정
│   └── .env.example          # 환경변수 예시
├── .github/
│   └── workflows/
│       ├── ci.yml            # 테스트 → 빌드 → Docker 이미지 검증
│       └── lint.yml          # ESLint + Gradle 컴파일 검사
└── .moai/
    └── specs/                # SPEC 문서 (SPEC-CMS-001 ~ SPEC-CMS-008)
```

---

## 사전 준비

| 도구 | 버전 | 설치 방법 |
|------|------|----------|
| Docker | 24+ | [docker.com](https://docker.com) |
| Docker Compose | V2 | Docker Desktop 포함 |
| Node.js | 22 LTS | [nodejs.org](https://nodejs.org) |
| pnpm | 9.x | `npm install -g pnpm@9` |
| Java (호스트) | 17 LTS | Gradle toolchain이 자동 다운로드 |

> Java는 호스트에 설치하지 않아도 됩니다. Gradle toolchain이 자동으로 JDK 17을 다운로드합니다.

---

## 빠른 시작 (로컬 개발)

### 1단계: Gradle wrapper 초기화 (최초 1회)

```bash
cd backend
gradle wrapper --gradle-version 8.10 --distribution-type bin
cd ..
```

### 2단계: PostgreSQL 기동

```bash
docker compose -f deploy/docker-compose.yml up -d postgres
```

### 3단계: 백엔드 실행

```bash
cd backend
./gradlew bootRun -Dspring.profiles.active=local
```

헬스 체크: http://localhost:8080/api/v1/health

### 4단계: 프론트엔드 실행

```bash
cd frontend
pnpm install
pnpm -F @iroum-cms/admin dev    # 관리자: http://localhost:5173
pnpm -F @iroum-cms/public dev   # 공개: http://localhost:5174
```

---

## Docker Compose 전체 스택 실행

모든 서비스를 컨테이너로 실행합니다.

```bash
# 이미지 빌드 후 기동
docker compose -f deploy/docker-compose.yml up -d --build

# 로그 확인
docker compose -f deploy/docker-compose.yml logs -f

# 중지
docker compose -f deploy/docker-compose.yml down
```

서비스 접속:

| 서비스 | URL |
|--------|-----|
| 공공 웹사이트 | http://localhost/ |
| 관리자 백오피스 | http://localhost/admin/ |
| API 헬스 체크 | http://localhost/api/v1/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| 관리자 (직접) | http://localhost:5173 |
| 공개 (직접) | http://localhost:5174 |

### Redis 활성화 (SPEC-CMS-002 본인인증 구현 시)

```bash
docker compose -f deploy/docker-compose.yml --profile cache up -d
```

---

## 환경변수 설정

```bash
cp deploy/.env.example deploy/.env
# deploy/.env 파일을 열어 실제 값으로 교체
```

주요 환경변수:

| 변수 | 설명 | 기본값 (로컬) |
|------|------|--------------|
| `SPRING_PROFILES_ACTIVE` | Spring 프로파일 | `local` |
| `DB_USERNAME` | DB 사용자 | `iroum_cms` |
| `DB_PASSWORD` | DB 비밀번호 | `iroum_cms_dev_pwd` |
| `JWT_SECRET` | JWT 서명 키 (256비트 이상) | 반드시 교체 필요 |

---

## 테스트 실행

### 백엔드

```bash
cd backend
./gradlew test                  # 단위 테스트
./gradlew test jacocoTestReport # 테스트 + 커버리지 리포트
```

커버리지 리포트: `backend/build/reports/jacoco/test/html/index.html`

### 프론트엔드

```bash
cd frontend
pnpm -F @iroum-cms/admin test   # 관리자 Vitest
pnpm -F @iroum-cms/public test  # 공개 Vitest
```

---

## 보안 — 개인정보 암호화 (PII)

**SPEC-CMS-SECURITY-PII-001 1차 적용 (2026-05-08)**

### 적용 범위

| 항목 | 내용 |
|------|------|
| 암호화 대상 | `users.email` 컬럼 (AES-256-GCM, 12-byte IV + 16-byte auth tag 분리 저장) |
| Lookup 방식 | HMAC-SHA256(`email_hmac`) — rainbow table 방지, B-tree UNIQUE 인덱스 |
| 마이그레이션 | V24: 5개 신규 PII 컬럼 + `email_hmac` UNIQUE 인덱스 + `data_dictionary` 시드 |
| 키 관리 | `PiiKeyVault` 인터페이스 (Local Dev: `LocalEnvPiiKeyVault`, 운영: KMS 후속 예정) |

### 환경변수 (개발 환경)

```bash
# base64-encoded 32 bytes (AES-256 암호화 키, 버전 1)
PII_EMAIL_KEY_V1=<base64-encoded-32-byte-key>

# base64-encoded 32 bytes (HMAC-SHA256 lookup 키, 암호화 키와 분리)
PII_EMAIL_HMAC_KEY=<base64-encoded-32-byte-key>
```

> 운영 환경에서는 `LocalEnvPiiKeyVault` 사용이 차단됩니다 (Spring profile `prod` + 환경변수 키 조합 부팅 거부). 운영 KMS 어댑터(SPEC-CMS-SECURITY-PII-KMS-001)가 필요합니다.

### 컴플라이언스

**개인정보보호법(PIPA) 제29조 안전성 확보 조치 의무** 충족.

- 제29조 — 개인정보의 안전한 보관: AES-256-GCM 암호화 (REQ-PII-EMAIL-001)
- 제29조 — 위·변조 방지: GCM auth tag 무결성 검증 + 실패 시 `audit_log` CRITICAL (REQ-PII-EMAIL-002)
- 제29조 — 접근 통제: HMAC lookup 전용 경로, 평문 email 직접 SELECT 금지 (REQ-PII-EMAIL-006)

### 후속 SPEC

| SPEC | 내용 |
|------|------|
| Step 5 (이행 대기) | `PiiEmailMigrationJob` 운영 배치 + V25 평문 컬럼 DROP |
| SPEC-CMS-SECURITY-PII-002 | 관리자 검색 제약 + API 응답 마스킹 + PII 접근 감사 |
| SPEC-CMS-SECURITY-PII-FOLLOWUP-001 | PII 비동기 감사 IT 검증 인프라 (@Disabled 3건 활성화) — Implemented 1차 |
| SPEC-CMS-SECURITY-PII-KMS-001 | AWS KMS / HashiCorp Vault 어댑터 |
| SPEC-CMS-SECURITY-PII-ROTATION-001 | 키 자동 회전 배치 |
| SPEC-CMS-SECURITY-PII-MASKING-001 | 로그/백업 마스킹 표준 — Logback 마스킹 + MDC SHA-256 + JWT log 정정 (백업은 후속) — **Implemented (1차) 2026-05-11** |

자세한 명세: `.moai/specs/SPEC-CMS-SECURITY-PII-001/spec.md`

---

**SPEC-CMS-SECURITY-PII-002 추가 적용 (2026-05-08)**

### 적용 범위

| 항목 | 내용 |
|------|------|
| Admin 검색 제약 | `email` 파라미터 와일드카드/부분 일치 거부 (400 ADMIN_EMAIL_PARTIAL_FORBIDDEN) — RFC 5321 valid email 패턴만 허용 |
| API 응답 마스킹 | `UserSummary`, `UserDetail` email 필드 `@JsonSerialize(EmailMaskSerializer)` — ADMIN/본인 평문, 그 외 길이별 마스킹 |
| 마스킹 규칙 | 1자=`*`, 2자=`**`, 3자+=첫CP+`***`+마지막CP, 코드 포인트 단위 (IDN/이모지 안전) |
| PII 접근 감사 | `findPage(actor)` 결과 본인 제외 후 `personal_data_access_log` 일괄 적재 (`recordBulk` @Async) |
| Architecture 강제 | ArchUnit으로 신규 DTO email 필드 마스킹 누락 자동 차단 |

### 응답 마스킹 예시

```
원본: john.doe@example.com
ADMIN 또는 본인 조회: john.doe@example.com (평문)
일반 사용자 조회: j***e@e***.com (3자+ 패턴)
local-part 1자(a@example.com): *@e***.com
local-part 2자(jo@example.com): **@e***.com
```

### 컴플라이언스

PIPA 제29조 안전성 확보 조치 의무 추가 완화 — SPEC-PII-001과 결합하여 운영 배포 차단 상태 완전 해소.

- 제29조 — 접근 통제: admin partial 검색 차단으로 전사 PII 노출 방지 (REQ-PII-EMAIL-007)
- 제29조 — 안전한 보관: 응답 마스킹으로 비인가 사용자 평문 노출 차단 (REQ-PII-EMAIL-008)
- 제29조 — 접근 기록: `personal_data_access_log` 일괄 적재로 admin lookup 추적성 확보 (REQ-PII-EMAIL-009)

자세한 명세: `.moai/specs/SPEC-CMS-SECURITY-PII-002/spec.md`

---

**SPEC-CMS-SECURITY-PII-MASKING-001 추가 적용 (2026-05-11)**

### 적용 범위 — 운영 노출 통제

| 항목 | 내용 |
|------|------|
| Logback 마스킹 | 모든 프로파일 적용. prod: `logstash-logback-encoder 7.4` `MaskingJsonGeneratorDecorator` + `RegexValueMasker`. dev/local: 자체 `PiiMaskingConverter` + `PatternLayout %maskedMsg` |
| 마스킹 패턴 4종 | email (`j***@***.***`), phone (`01*-****-XXXX`), SSN (`XXXXXX-*******`), IPv4 (`XXX.XXX.***.***`) |
| MDC PII 필드 | `clientIp`/`ip` → SHA-256 hex prefix 8자 (디버깅 추적성 + PII 보호 양립). `userId`/`traceId`/`spanId`/`requestId`/`userAgent`는 평문 보존 |
| JWT 인증 로그 | `JwtAuthenticationFilter:116` username 제거, `userId`만 출력 (DEBUG 활성화 시에도 안전) |

### 기대 효과

- 운영 ELK/Loki 등 외부 로그 수집 시스템에 PII 평문 미전송
- DEBUG 레벨 일시 활성화 시에도 PII 노출 위험 차단
- MDC SHA-256 prefix로 동일 사용자 추적 가능 (디버깅 식별성 유지)

### 컴플라이언스

PIPA 제29조 안전성 확보 조치 의무 추가 완화 — PII-001(저장 영역) + PII-002(응답 영역) + PII-MASKING-001(운영 부수 채널) 결합으로 운영 환경 PII 노출 위험 완전 통제.

- 제29조 — 안전한 보관: 운영 로그 PII 평문 저장 차단 (REQ-PII-MASK-001)
- 제29조 — 접근 통제: MDC SHA-256 prefix로 디버깅 시 평문 노출 차단 (REQ-PII-MASK-002)
- 제29조 — 안전한 처리: JWT 인증 로그 PII 최소화 (REQ-PII-MASK-003)

자세한 명세: `.moai/specs/SPEC-CMS-SECURITY-PII-MASKING-001/spec.md`

---

## SPEC 문서

| SPEC ID | 제목 | 상태 |
|---------|------|------|
| SPEC-CMS-001 | 공공기관 CMS — 1차 출시 기반 (Umbrella) | Implemented (1차 출시 완료) |
| SPEC-CMS-002 | 회원·권한·로그인 상세 (Bundle A — Auth, Account, Authorization) | Implemented |
| SPEC-CMS-003 | 게시판·공지·Q&A·FAQ 상세 (Bundle B) | Implemented |
| SPEC-CMS-004 | 콘텐츠·메뉴·사이트관리 상세 (Bundle C) | Implemented |
| SPEC-CMS-005 | 통계·로그·시스템관리 상세 (Bundle D) | Implemented |
| SPEC-CMS-006 | 안전경영 가이드라인 + 사고사례 매칭 (Safety Management + Incident Matching) | Implemented |
| SPEC-CMS-007 | 정책사업 지능형 매칭 + 적기 타겟팅 알림 (Policy Matching + Timing Notification) | Implemented |
| SPEC-CMS-008 | 시각화 대시보드 + KPI 통합 | Implemented |
| SPEC-CMS-009 | 데이터 거버넌스 (Data Governance) | Implemented |
| SPEC-CMS-010 | 통합 검색 (Unified Search) | Implemented |
| SPEC-CMS-MEDIA-001 | 통합 미디어 라이브러리 | Implemented |
| SPEC-CMS-SECURITY-PII-001 | 개인정보 암호화 (Email AES-256-GCM + HMAC + 키 관리) | Implemented (1차) |
| SPEC-CMS-SECURITY-PII-002 | PII 노출 통제 (Admin 검색 partial 차단 + 응답 마스킹 + PII 접근 감사 보강) | Implemented (1차) |
| SPEC-CMS-SECURITY-PII-FOLLOWUP-001 | PII 비동기 감사 IT 검증 인프라 정비 (@Disabled 3건 활성화) | Implemented (1차) |
| SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 | HTTP 권한 매트릭스 IT 인프라 (운영 SecurityFilterChain + @PreAuthorize 회귀 검증) | Implemented (1차) |
| SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 | ControllerTest 메소드 레벨 401/403 회귀 보강 (12 적용 + 19 IT 위임) | Implemented (1차) |
| SPEC-CMS-TEST-INFRA-RECONFIG-001 | JaCoCo + check + CI integrationTest 통합 (5/7 C2 잔여 갭 3건 해소) | Implemented (1차) |
| SPEC-CMS-SECURITY-PII-MASKING-001 | PII 운영 노출 통제 (Logback 마스킹 + MDC SHA-256 + JWT log 정정) | Implemented (1차) |
| SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 | HTTP 권한 매트릭스 IT 확장 (29 endpoint × 12 권한 어휘 100% 회귀 검출, 89 @Test) | Implemented (1차) |
| SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 | ArchUnit 기반 운영 @PreAuthorize 자동 검출 (54 endpoint baseline + 31 권한 어휘 baseline, 4 AC) | Implemented (1차) |
| SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002 | HTTP 권한 매트릭스 IT 확장 2차 (19 어휘 × 57 AC + ArchUnit 31 어휘 100% IT 커버, 분리 회귀 4건) | Implemented (1차) |
| SPEC-CMS-SECURITY-PII-FOLLOWUP-002 | PII-FOLLOWUP-001 잔여 RED 분리 (@MockitoSpyBean + @Async 충돌 해소 + Fallback Unit test) | Implemented (1차) |
| SPEC-CMS-SECURITY-PII-FOLLOWUP-003 | PII Audit IT 잔여 2 AC 해소 (옵션 G TRUNCATE cleanup, 핵심 2 AC GREEN) | Implemented (1차) |
| SPEC-CMS-SECURITY-PII-FOLLOWUP-004 | AC-009-3/4 false GREEN 정밀 진단 (AC-009-3/4 GREEN, AC-009-2 race condition 잔여) | Mostly Implemented |
| SPEC-CMS-SECURITY-PII-FOLLOWUP-005 | PiiAuditEnhanceIT AC-009-2 race condition 정밀 진단 (분리) | Planned |

SPEC 문서 위치: `.moai/specs/`

---

## HTTP 권한 매트릭스 IT 신규 endpoint 추가 절차 (ArchUnit 자동 검출 + 수동 갱신)

**SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 적용 후 (2026-05-11~)**: 운영에 신규 `@PreAuthorize` 추가 시 `AuthorizationCoverageArchTest`가 자동으로 RED 검출 → CI PR 차단. 수동 절차는 RED 신호에 따라:

1. **ArchUnit RED 신호 감지** (`./gradlew :backend:test --tests "kr.co.ircp.cms.security.archunit.AuthorizationCoverageArchTest"`):
   - AC-AAD-001-1 RED → 운영 @PreAuthorize 카운트 변경 (신규 추가/제거)
   - AC-AAD-002-1 RED → IT 시나리오 누락 또는 baseline 갱신 필요
   - AC-AAD-003-1 RED → 신규 권한 어휘 등장 또는 기존 어휘 제거

2. **권한 어휘 분류**:
   - `hasRole/hasAnyRole` (역할 기반): SUPER_ADMIN/DEPT_ADMIN/ADMIN/CONTENT_ADMIN — 해당 도메인 그룹
   - `hasAuthority` (권한 기반, 31종 운영 실측): CONTENT:WRITE/READ, PAGE:WRITE/READ/PUBLISH/ROLLBACK/HISTORY:READ, TEMPLATE:WRITE/READ, BLOCK:WRITE, MENU:WRITE/PERMISSION:WRITE, SITE:WRITE, USER:READ, SYSTEM:* (READ/CODE:READ/CODE:WRITE/STATS/DASHBOARD/SETTING:READ/SETTING:WRITE/MAINT:READ/MAINT:WRITE/LOG:READ/ADMIN), AUDIT:READ
   - `isAuthenticated()`: 권한 무관 (Auth 도메인, 401/200만 검증, 403 N/A)

3. **AuthorizationMatrixExpandIT 시나리오 추가** (3 시나리오: 401/403/200 또는 isAuthenticated은 401/200만)

4. **AuthorizationCoverageArchTest baseline 갱신**:
   - `baselineEndpoints()`: 신규 endpoint 추가
   - `baselineAuthorityVocabularies()`: 신규 어휘 추가 (필요 시)

5. **GREEN 재확인**: `./gradlew :backend:test --tests "kr.co.ircp.cms.security.archunit.AuthorizationCoverageArchTest"`

자세한 명세: `.moai/specs/SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001/spec.md` + `.moai/specs/SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001/spec.md`

---

## 라이선스

본 프로젝트는 [LICENSE](LICENSE) 조건에 따라 배포됩니다.

## 문의

- 개발팀: admin@ircp.co.kr
- 이슈 트래커: GitHub Issues
