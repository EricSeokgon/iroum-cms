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
| SPEC-CMS-SECURITY-PII-KMS-001 | AWS KMS / HashiCorp Vault 어댑터 |
| SPEC-CMS-SECURITY-PII-ROTATION-001 | 키 자동 회전 배치 |
| SPEC-CMS-SECURITY-PII-MASKING-001 | 로그/백업 마스킹 표준 |

자세한 명세: `.moai/specs/SPEC-CMS-SECURITY-PII-001/spec.md`

---

## SPEC 문서

| SPEC ID | 제목 | 상태 |
|---------|------|------|
| SPEC-CMS-001 | 공공기관 CMS 1차 출시 기반 (Umbrella) | Draft |
| SPEC-CMS-002 | 회원·인증·권한 관리 | 예정 |
| SPEC-CMS-003 | 게시판·공지·Q&A·FAQ | 예정 |
| SPEC-CMS-004 | 콘텐츠·메뉴·사이트 관리 | 예정 |
| SPEC-CMS-005 | 통계·로그·시스템 관리 | 예정 |
| SPEC-CMS-MEDIA-001 | 미디어·첨부파일 관리 | 예정 |
| SPEC-CMS-SECURITY-PII-001 | 개인정보 암호화 (Email AES-256-GCM + HMAC + 키 관리) | Implemented (1차) |

SPEC 문서 위치: `.moai/specs/`

---

## 라이선스

본 프로젝트는 [LICENSE](LICENSE) 조건에 따라 배포됩니다.

## 문의

- 개발팀: admin@ircp.co.kr
- 이슈 트래커: GitHub Issues
