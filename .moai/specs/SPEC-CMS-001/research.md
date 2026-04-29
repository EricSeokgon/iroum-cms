# SPEC-CMS-001 Research Notes

> 본 문서는 SPEC-CMS-001 작성 과정에서 검토한 주요 기술적 의사결정과 근거를 기록한다.
> 결정 사항 자체는 spec.md / tech.md (FROZEN) 에 명시되며, 본 문서는 사유와 대안 분석을 제공한다.

---

## 1. egovframe v5.0 공통컴포넌트 차용 범위

### 의사결정

egovframe 5.0.0 + 공통컴포넌트 v5의 **백엔드 로직(엔티티 모델·SQL 쿼리·비즈니스 규칙)** 만 차용하고, 모든 화면(JSP) 은 폐기 후 Vue 3 + Element Plus로 신규 구축한다.

### 근거

- 공통컴포넌트의 JSP 화면은 jQuery 기반·테이블 레이아웃·반응형 미지원으로 KWCAG 2.2 AA 준수에 부적합
- 백엔드 도메인 모델(Mapper XML, Service 흐름, 코드 그룹) 은 공공기관 도메인을 충실히 반영하므로 차용 가치 높음
- Spring 5 → Spring 6 이관, Java 8 → 17 마이그레이션은 공통컴포넌트 v5에서 이미 처리됨

### 차용 매트릭스

| 자산 유형 | 차용 여부 | 비고 |
|----------|----------|------|
| Mapper XML (SQL) | 차용 (PostgreSQL 변환) | 변환 매트릭스는 §2 참조 |
| Service / VO 클래스 | 부분 차용 | Spring Security 6 + JWT 적용을 위해 일부 재작성 |
| Controller | 신규 작성 | REST 응답 표준화 (ApiResponse), OpenAPI 어노테이션 |
| JSP 화면 | 폐기 | Vue 3 SPA로 전면 대체 |
| 공통 코드 시드 | 차용 | `V2__seed_codes.sql` 로 이관 |

### 대안 검토

- (대안 A) 공통컴포넌트 전면 신규 개발: 도메인 누락·비표준화 위험 높음. 거부
- (대안 B) 공통컴포넌트 화면까지 그대로 유지: KWCAG 미달, 운영자 UX 불만 지속. 거부

---

## 2. PostgreSQL 16 vs Oracle / MariaDB

### 의사결정

PostgreSQL 16 채택 (tech.md FROZEN).

### 근거

- 라이선스 비용 0원, 공공기관 오픈소스 전환 정책 부합
- JSONB·CTE·윈도우 함수·고급 인덱스(GIN, BRIN) 지원
- 공공기관(국세청·행정안전부 사례 등) 도입 사례 다수
- Spring Boot 3.2 + Flyway 10 + Testcontainers 호환성 검증됨

### 공통컴포넌트 DDL 변환 매트릭스 (Oracle/Tibero → PostgreSQL)

| Oracle/Tibero | PostgreSQL | 변환 주의점 |
|--------------|-----------|------------|
| `VARCHAR2(N)` | `VARCHAR(N)` | 문자 단위 길이는 동일하지만 PostgreSQL은 문자 인코딩에 따라 바이트 차이 가능 |
| `NUMBER(p,s)` | `NUMERIC(p,s)` | 정수형은 `BIGINT`로 단순화 권장 |
| `NUMBER` (정밀도 미지정) | `NUMERIC` 또는 `BIGINT` | 도메인 의미에 따라 분기 |
| `DATE` (Oracle은 시간 포함) | `TIMESTAMP` | Oracle DATE = PostgreSQL TIMESTAMP. PostgreSQL DATE는 시간 미포함이므로 주의 |
| `SYSDATE` | `CURRENT_TIMESTAMP` | 함수 자체 변경 |
| `SEQUENCE` + `BEFORE INSERT TRIGGER` | `GENERATED ALWAYS AS IDENTITY` 또는 `BIGSERIAL` | 신규 채번은 IDENTITY 권장 |
| `ROWNUM` 페이징 | `LIMIT n OFFSET m` | 또는 `OFFSET FETCH` |
| `CLOB`, `BLOB` | `TEXT`, `BYTEA` | 대용량은 외부 스토리지 권장 |
| `DECODE(...)` | `CASE WHEN ... THEN ... END` | 표준 SQL로 변환 |
| `NVL(a, b)` | `COALESCE(a, b)` | |
| `DUAL` | (제거) | PostgreSQL은 SELECT만 호출 가능 |
| `CONNECT BY ...` | `WITH RECURSIVE` CTE | 메뉴 트리 등에 사용 |
| Trigger 기반 자동채번 | `IDENTITY` 컬럼으로 단순화 | |

### 변환 작업 순서

1. 공통컴포넌트 v5 원본 DDL 추출 (Oracle/Tibero용)
2. 위 매트릭스 적용 + 수동 검토
3. Flyway `V1__init_schema.sql` 로 통합 작성
4. Testcontainers + JUnit 통합 테스트로 스키마·기본 CRUD 검증
5. 공통 코드 시드는 별도 `V2__seed_codes.sql` 로 분리

---

## 3. JWT vs Session-Cookie

### 의사결정

JWT (Access + Refresh Token Rotation) + Refresh Token HttpOnly Cookie.

### 근거

- Stateless API: 수평 확장 시 세션 공유 불필요
- React/Vue SPA + 모바일 앱(향후) 동일 인증 방식 적용 가능
- egovframe 공식 보안 컴포넌트(uat/uia) 와 호환되는 구조

### 보안 강화 방안

| 위협 | 대응 |
|------|------|
| Access Token 탈취 | 만료 15분 단축 + IP 변경 시 강제 재인증 |
| Refresh Token 탈취 | HttpOnly + Secure + SameSite=Strict Cookie 저장, JS 접근 차단 |
| Refresh Token 재사용 | Rotation 정책: 사용된 Refresh는 즉시 blacklist 등록 |
| XSS | DOMPurify(콘텐츠 입력) + CSP 헤더 + Vue 자동 이스케이프 |
| CSRF | Stateless API라 일반 CSRF 불가, Refresh는 SameSite=Strict로 보호 |
| 토큰 변조 | RSA 또는 강력한 HMAC 비밀키, 키는 환경변수/시크릿 매니저 주입 |

### 대안 검토

- (대안 A) Spring Session + Redis: 세션 클러스터링 단순. 그러나 Redis 운영 비용·SPA 친화도 낮음
- (대안 B) OAuth2/OIDC (Keycloak): 외부 IdP 도입 부담, 1차 범위 초과. 후속 SPEC에서 검토 가능

---

## 4. Vue 3 + Element Plus vs React + Material UI

### 의사결정

Vue 3.5+ + Element Plus 2.8+ (tech.md FROZEN).

### 근거

- Element Plus는 공공기관 백오피스 핵심 컴포넌트(테이블, 폼, 트리, 트랜스퍼, 페이지네이션) 가 즉시 사용 가능
- Vue 3 Composition API + `<script setup>` 으로 간결한 SFC 구조
- TypeScript 네이티브 지원, Vite 6 빌드 속도 우수
- 공공기관·SI 업계에서 Vue 도입 사례가 React 못지않게 많아 인력 수급 문제 없음

### 트레이드오프

- 글로벌 생태계(라이브러리 수) 는 React보다 작음. 본 프로젝트의 요구 컴포넌트는 Element Plus + Tailwind로 충분하므로 영향 없음
- Element Plus 기본 색상은 푸른 계열 → 공공기관 브랜드 적용 시 SCSS 변수 오버라이드 필요

### 대안 검토

- (대안 A) React + Material UI: Vue Element Plus만큼 즉시성 있지만 학습 곡선·번들 크기 부담
- (대안 B) Svelte / SolidJS: 생태계 미성숙, 공공기관 도입 사례 부족

---

## 5. KWCAG 2.2 AA 자동 검증 도구

### 검토 도구

| 도구 | 장점 | 한계 |
|------|------|------|
| axe-core | Playwright/Vitest 통합 용이, 오탐률 낮음 | 자동화로 약 30~40% 위반만 감지 (수동 보완 필요) |
| Lighthouse | Google 공식, 종합 점수 제공 | 접근성만 별도 분석 어려움, CI 통합 복잡 |
| WAVE | 시각적 리포트 우수 | API 미공개, 자동화 제약 |
| Pa11y | CLI 자동화 친화 | axe 기반이라 axe-core 직결로 충분 |

### 채택 방안

- Playwright E2E + `@axe-core/playwright` 라이브러리
- 모든 라우트별 접근성 검사 시나리오 자동 실행
- critical / serious 위반 발견 시 CI 빌드 실패
- 분기별 수동 감사: 키보드 네비게이션, 스크린리더(NVDA) 점검, 색약 시뮬레이션

### 추가 정책

- 색대비 4.5:1 자동 검사 (axe-core)
- 모든 input에 label 또는 aria-label 강제
- 페이지마다 단일 `<h1>` + 의미적 헤딩 구조
- 키보드 포커스 표시 자체 CSS 보장 (`:focus-visible` 강제 스타일링)

---

## 6. 감사로그 구현 방식 — Spring AOP 어노테이션 패턴

### 의사결정

`@Audited` 커스텀 어노테이션 + `AuditLogAspect` AOP 클래스로 횡단 구현.

### 구현 개요

- 감사 대상 메서드에 `@Audited(domain="board", action="CREATE")` 어노테이션 부여
- `AuditLogAspect`가 `@Around` 로 감싸서 진입·종료 시점 캡처
- 캡처 정보: 사용자 ID(SecurityContext), IP(HttpServletRequest), 클래스/메서드명, 파라미터 요약(민감 정보 마스킹), 결과 코드, 처리 시간
- 비동기 INSERT (`@Async` + 별도 트랜잭션) 로 본 비즈니스 트랜잭션과 분리

### 변조 방지

- audit_log 테이블에 대해 DB 사용자 권한을 INSERT only 로 제한
- `cms_app` 사용자: INSERT 가능, UPDATE/DELETE/TRUNCATE 거부
- 별도 `cms_audit_admin` 사용자: SELECT 가능 (운영자 조회용)

### 대안 검토

- (대안 A) Hibernate Envers: JPA 종속, 본 프로젝트는 MyBatis 사용 → 부적합
- (대안 B) DB Trigger: 데이터 변경에는 강력하나 비즈니스 컨텍스트(사용자 ID, traceId) 캡처 어려움

---

## 7. PIA 대응 — 마스킹·암호화·보존기간

### 암호화 정책

| 데이터 유형 | 정책 |
|------------|------|
| 비밀번호 | BCrypt (strength=12), 단방향 해시 |
| 주민등록번호 | AES-256-GCM 양방향 암호화, 컬럼명 `*_enc` |
| 휴대폰번호 | AES-256-GCM (조회 가능성 위해 양방향) |
| 이메일 | AES-256-GCM (마스킹된 검색을 위해 별도 hash 컬럼 부가 가능) |
| 키 관리 | 환경변수 또는 외부 시크릿 매니저(AWS KMS, Hashicorp Vault) |

### 마스킹 정책

| 권한 | 휴대폰 | 이메일 | 주민번호 |
|------|--------|--------|---------|
| 일반 운영자 | `010-****-5678` | `ab***@example.com` | `XXXXXX-1******` |
| 보안 담당자 | 평문 (감사로그 기록 동반) | 평문 | 평문 |
| 시스템 자기 자신 (로그인 본인) | 본인 정보는 평문 | 평문 | 평문 |

### 보존기간

| 데이터 | 보존 기간 | 만료 정책 |
|-------|----------|----------|
| 사용자 계정 (탈퇴 후) | 5년 | 자동 익명화 batch (별도 SPEC) |
| 감사로그 | 5년 (또는 기관 정책) | 6개월 후 콜드 스토리지 이관 |
| 접속로그 | 1년 | 1년 후 자동 삭제 |
| 첨부파일 | 게시글 수명 동일 | 게시글 영구삭제 시 동시 삭제 |

### 권한별 노출 제어

- API 응답 직렬화 단계에서 사용자 권한 확인 후 마스킹 적용 (Jackson custom serializer 또는 Service 레이어 변환)
- 마스킹 해제 조회는 별도 엔드포인트(`?unmask=true`) + 보안 담당자 권한 + audit_log 강제 기록

---

## 8. Docker 다단계 빌드 전략

### Dockerfile.backend (Multi-stage)

```
Stage 1 (builder): eclipse-temurin:17-jdk-alpine
  - Gradle wrapper + 의존성 캐시 활용
  - ./gradlew bootJar

Stage 2 (runtime): eclipse-temurin:17-jre-alpine
  - 빌드 산출물 (build/libs/*.jar) 만 복사
  - 비-root 사용자(`appuser`) 로 실행
  - HEALTHCHECK: curl http://localhost:8080/actuator/health
```

### Dockerfile.admin / Dockerfile.public (Multi-stage)

```
Stage 1 (builder): node:20-alpine
  - pnpm install --frozen-lockfile
  - pnpm build (vite)

Stage 2 (runtime): nginx:alpine
  - dist/ 정적 파일 → /usr/share/nginx/html/
  - nginx.conf 커스터마이징 (gzip, 캐시, history fallback)
```

### 이미지 크기 목표

| 이미지 | 목표 크기 | 비고 |
|--------|----------|------|
| backend | < 250MB | jre-alpine 기반 |
| admin-fe | < 50MB | nginx-alpine + dist |
| public-fe | < 50MB | nginx-alpine + dist |

### docker-compose 구성 원칙

- 로컬 개발(`docker-compose.yml`): 핫리로드 볼륨 마운트, 디버그 포트 노출
- 운영(`docker-compose.prod.yml`): 리소스 제한 (mem_limit), restart 정책 (`unless-stopped`), 환경변수는 외부 `.env.production` 또는 시크릿 주입

---

## 9. RFP 통합 분석 (v0.2 amendment, 2026-04-29)

### 9.1 RFP 출처와 적용 원칙

- **출처**: 중소벤처기업진흥공단 (KOSME) 비즈패스파인더 고도화 용역 RFP (2026-04-23, 53페이지)
- **적용 원칙**: 기능 요구사항 (SFR-001~015 + 일부 INR/DAR/SER/COR) 만 채택, **기술 스택은 본 SPEC v0.1의 결정안 (Vue 3.5 / Spring Boot 3.2.x / Java 17 / egovFrame v5.0.0 / PostgreSQL 16 / JWT) 그대로 유지**
- **사용자 결정 (2026-04-29)**: archive 안 함, amendment로 진행, 옵션 SPEC 트랙 분리 (AI/ML, SSO 마이그레이션)

### 9.2 채택 / 채택 제외 비교

| 영역 | RFP 명세 | iroum-cms 결정 | 처리 방식 |
|---|---|---|---|
| RDBMS | MariaDB | PostgreSQL 16 | tech.md FROZEN 유지 |
| 벡터 DB | Milvus | (CMS 본 트랙 미사용) | 옵션 SPEC-CMS-AI-001 |
| FE 표현계층 | JSP 2.1 | Vue 3.5 SPA | 본 SPEC §6 유지 |
| WAS | Apache + 외부 Tomcat | Spring Boot 내장 Tomcat 10 + Nginx | 본 SPEC §5 유지 |
| 인증 | 상급기관 통합로그인 SSO API | JWT 기반 (옵션 SSO 별도) | 옵션 SPEC-CMS-MIG-001 |
| 파일 저장 | 통합파일서버 | Local FS / 객체 스토리지 | 단일 기관 범위 단순화 |

### 9.3 RFP 69개 요구사항 적용 정책

| 분류 | 개수 | iroum-cms 적용 정책 |
|---|---:|---|
| SFR (기능) | 15 | 신규 SPEC 5개 (006~010) + 기존 SPEC 002~005 amendment + 옵션 2개로 완전 커버 |
| PER (성능) | 4 | 모든 child SPEC 비기능 섹션에 일괄 반영 (PER-002~004) |
| INR (인터페이스) | 12 | SPEC-CMS-008 시각화 / SPEC-CMS-010 검색에 흡수 |
| DAR (데이터) | 10 | SPEC-CMS-009 데이터 거버넌스에 흡수 |
| SER (보안) | 7 | 기존 SPEC-CMS-002 / SPEC-CMS-003 보안 정책 강화 |
| COR (제약) | 6 | KWCAG 2.2 (REQ-CROSS-001), 시큐어 코딩 (REQ-CROSS-002) 이미 반영 |
| TER / QUR / PMR / PSR (운영) | 15 | 프로젝트 운영 단계 항목, SPEC에 직접 영향 없음 (단 QUR-004 결함률은 acceptance.md QG에 반영) |

### 9.4 다음 amendment 일정

1. **SPEC-CMS-001 v0.2** (본 작업 — §15~17 추가, §14 갱신)
2. **SPEC-CMS-002~005 amendment v0.2** — 기존 4개 SPEC 일괄 RFP 비기능·SFR 매핑 추가
3. **신규 SPEC P0 (006/007/008)** — 안전경영·정책사업·시각화 대시보드 (병렬 가능)
4. **신규 SPEC P1 (009/010)** — 데이터 거버넌스·통합 검색
5. **(옵션)** SPEC-CMS-AI-001 — AI/ML + Milvus, 별도 사용자 승인 시점 착수
6. **(옵션)** SPEC-CMS-MIG-001 — 상급기관 SSO + 통합 홈페이지 이관, 별도 사용자 승인 시점 착수

### 9.5 RFP 응찰 vs 자체 프로젝트 정체성

본 iroum-cms는 RFP 응찰 산출물이 아닌 **자체 공공기관 CMS 프로젝트**로 정의된다. RFP는 기능 요구사항의 구체성을 차용하기 위한 참고 자료로 활용하며, RFP의 인프라·계약·검수 조건(PMR-003 spir.kr 등록, 기능점수 측정 전문가 등)은 본 SPEC 범위 밖이다. 단 추후 RFP 응찰 또는 유사 공공기관 도입 시에는 본 SPEC §17.5의 기술 상향 사유를 활용할 수 있다.

---

## 10. 홍익인간 CMS Gap 분석 통합 (v0.3 amendment, 2026-04-29)

### 10.1 분석 배경

홍익인간 CMS (https://www.yooncoms.com/cms)는 GS인증 1등급, 나라장터 등록 제품으로 공공기관 CMS 사례 학습 목적. 분석 결과 16개 차별 기능 중 9개가 iroum-cms와 갭이 있었음.

### 10.2 채택 의사결정 표

| 갭 | 결정 | 사유 |
|---|---|---|
| 본인인증 OTP | 채택 | 가입·중요 변경 시 표준, 개인정보보호법 강화 |
| 회원정보 접근 로그 | 채택 | 일반 audit_log와 분리하여 검색 성능·법적 추적성 |
| 통합 미디어 라이브러리 | 채택 | 첨부파일 중복 제거·EXIF 자동화·KWCAG alt_text 통합 |
| GA4 연동 | 미채택 | 자체 KPI(SPEC-005) 충분, 외부 의존 회피 |
| OAuth SNS | 미채택 | SSO Provider(SPEC-002 v0.2) 어댑터로 후속 |
| 페이지 롤백 UI | 미채택 | 모델 있음, UI는 후속 |
| 더블린 코어 | 미채택 | S-Meta/DA# 우선 |
| 일정/예약 | 미채택 | 별도 도메인 |
| 플러그인 | 미채택 | 1차는 모놀리식 |
| 멀티사이트 | 미채택 | 2차 활성화 |
| 스킨 패키지 | 미채택 | 후속 |

### 10.3 SPEC-CMS-MEDIA-001 신규 SPEC 도입 사유

- 첨부파일(SPEC-003 bbs_attachment)과 별개 — 게시판/페이지/팝업 공유
- EXIF/WebP/AV 스캔 자동화 표준화
- 사용처 추적으로 안전한 삭제·교체
- 홍익인간 CMS 대비 차별화 포인트로 활용

### 10.4 v0.3 이후 로드맵

- v0.4 (예상): SPEC-CMS-006/007/008 (RFP P0 신규) 완성
- v0.5: GA4 + OAuth SNS + 페이지 롤백 UI + 더블린 코어 추가 검토
- v1.0: 멀티사이트 활성화 + 스킨 패키지
- v2.0: 플러그인 아키텍처

---

_문서 버전: v0.3_
_작성일: 2026-04-29 (v0.3: 홍익인간 CMS gap 분석 통합)_
_이전 버전: v0.2 (RFP 통합 분석)_
_다음 단계: SPEC-CMS-002 v0.3 amendment + SPEC-CMS-MEDIA-001 신규 작성_
