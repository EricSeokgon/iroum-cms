# iroum-cms 기술 문서

> 상세 요구사항은 SPEC-CMS-001 참조 (작성 예정)
> 아래 스택은 FROZEN — SPEC 작성 이후 변경 시 이 문서와 동시 수정 필요

---

## 1. 백엔드 스택

| 레이어 | 기술 | 버전 | 선택 이유 |
|--------|------|------|----------|
| 표준 프레임워크 | egovframe | 5.0.0 | 전자정부 표준 의무 준수, 공통컴포넌트 v5 호환 |
| JDK | OpenJDK | 17 LTS | LTS 장기 지원, Virtual Thread 지원, egovframe 5 공식 지원 버전 |
| 어플리케이션 프레임워크 | Spring Framework | 6.x | egovframe 5.0이 Spring 6 기반, Jakarta EE 9+ 네임스페이스 |
| 부트 레이어 | Spring Boot | 3.5.x (latest stable patch) | 자동 구성, 내장 Tomcat, Spring 6 대응 버전 |
| 보안 | Spring Security | 6.x | Spring 6 네이티브, JWT + Refresh Token 필터 체인 구성 |
| HTML Sanitizer | jsoup | 1.17.2 | OWASP XSS 방어 — RICH_TEXT 서버사이드 Sanitize, Jsoup Safelist.relaxed() 기반 |
| ORM | MyBatis | 3.5.x | egovframe 공통컴포넌트 SQL Mapper 표준 준수, XML 기반 쿼리 |
| 데이터베이스 | PostgreSQL | 16 | JSONB, 고성능, 오픈소스, 공공기관 도입 사례 다수 |
| WAS | Tomcat (내장) | 10.x | Jakarta EE 9+ 서블릿 6.0 지원, Spring Boot 내장 |
| API 명세 | springdoc-openapi | 2.x | OpenAPI 3.1 자동 생성, Swagger UI 통합 |
| i18n (BE) | Spring MessageSource | Spring 6 내장 | ko/en 메시지 프로퍼티 로드, 표준 구현체 |
| DB 마이그레이션 | Flyway | 10.x | SQL 기반 버전 관리, PostgreSQL 지원, Spring Boot 자동 구성 |
| 커넥션 풀 | HikariCP | Spring Boot 내장 | 고성능, 경량, Spring Boot 기본 풀 |
| 로깅 | Logback | Spring Boot 내장 | JSON 포맷 출력(운영), 콘솔 포맷(개발) |
| 테스트 (단위) | JUnit 5 + Mockito | Spring Boot 내장 | 표준 Java 테스트 프레임워크 |
| 테스트 (통합) | Testcontainers (PostgreSQL) | 1.19.x | 실제 DB 기반 통합 테스트, 환경 격리 |

---

## 2. 프론트엔드 스택

| 레이어 | 기술 | 버전 | 선택 이유 |
|--------|------|------|----------|
| 프레임워크 | Vue | 3.5+ | Composition API, TypeScript 네이티브 지원, 경량 번들 |
| 언어 | TypeScript | 5.x | 정적 타입 안전성, IDE 지원, 대규모 SPA 유지보수성 |
| 번들러 | Vite | 6 | ESM 기반 HMR 초고속, Vue 공식 권장 빌드 도구 |
| 상태 관리 | Pinia | latest | Vuex 공식 후계자, TypeScript 완전 지원, DevTools 연동 |
| 라우터 | Vue Router | 4 | Vue 3 공식 라우터, 코드 스플리팅, 가드 기반 접근 제어 |
| UI 라이브러리 | Element Plus | 2.8+ | Vue 3 전용, 공공기관 백오피스 UI 패턴에 적합한 컴포넌트 집합 |
| CSS 프레임워크 | Tailwind CSS | 3.x (4 안정화 시 검토) | 유틸리티 퍼스트, 번들 크기 최소화, 커스텀 디자인 시스템 용이 |
| i18n (FE) | vue-i18n | 9.x | Vue 3 공식 i18n 라이브러리, ko/en 동적 전환 |
| API 클라이언트 | Axios | 1.x | HTTP 인터셉터 기반 토큰 갱신, OpenAPI 생성 클라이언트와 통합 |
| 테스트 (단위) | Vitest + Vue Test Utils | latest | Vite와 동일 설정, 빠른 단위·컴포넌트 테스트 |
| 테스트 (E2E) | Playwright | 1.x | 크로스 브라우저, 접근성 검사 통합, CI 친화적 |
| XSS 방어 | DOMPurify | ^3.1.6 | v-html 렌더링 시 클라이언트사이드 XSS 방어, Admin SPA useSafeHtml composable |

---

## 3. 데이터베이스

### PostgreSQL 16

- 사용 이유: JSONB 컬럼(메타데이터 저장), 고성능 쿼리 플래너, 공공기관 도입 사례, 오픈소스
- 문자셋: UTF-8, Locale: ko_KR.UTF-8

### 마이그레이션: Flyway

- 마이그레이션 파일 위치: `backend/src/main/resources/db/migration/`
- 명명 규약: `V{숫자}__{설명}.sql` (예: `V1__init_schema.sql`)
- Spring Boot 기동 시 자동 실행 (`spring.flyway.enabled=true`)

### 공통컴포넌트 DDL 변환 노트 — 초안, 검토 필요

egovframe 공통컴포넌트 v5는 Oracle 또는 Tibero 기준 DDL을 제공합니다. PostgreSQL 변환 시 주의 사항:

| Oracle/Tibero 패턴 | PostgreSQL 대체 |
|-------------------|----------------|
| `VARCHAR2(N)` | `VARCHAR(N)` |
| `NUMBER(p,s)` | `NUMERIC(p,s)` 또는 `BIGINT` |
| `SYSDATE` | `CURRENT_TIMESTAMP` |
| `SEQUENCE` + `TRIGGER` | `GENERATED ALWAYS AS IDENTITY` |
| `ROWNUM` 페이징 | `LIMIT` / `OFFSET` |
| CLOB | `TEXT` |

- 변환된 DDL은 Flyway V1 파일로 관리
- 공통컴포넌트 기본 코드(ccm) 시드 데이터는 별도 `V2__seed_codes.sql`로 분리

### HikariCP 풀 설정 (운영 권장값)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

---

## 4. 인증·보안

### JWT 인증 흐름

```
[클라이언트]
    │
    ├─ POST /api/v1/auth/login (ID/PW)
    │       ↓
    │   [Spring Security]
    │       → BCrypt 비밀번호 검증
    │       → Access Token (15분) + Refresh Token (7일) 발급
    │       → Refresh Token: HttpOnly Secure Cookie로 전송
    │       → Access Token: 응답 바디로 전송 (클라이언트 메모리 보관)
    │
    ├─ API 요청 시: Authorization: Bearer {AccessToken}
    │       ↓
    │   [JwtAuthenticationFilter]
    │       → 토큰 유효성 검증 → SecurityContext 설정
    │
    └─ Access Token 만료 시: POST /api/v1/auth/refresh
            → HttpOnly Cookie의 Refresh Token 검증
            → 새 Access Token 발급
```

### 보안 구성 요소

| 항목 | 기술·설정 |
|------|----------|
| 비밀번호 해싱 | BCryptPasswordEncoder (strength 12) |
| CSRF 보호 | Stateless REST API → CSRF 비활성화. Refresh Token은 HttpOnly Cookie + SameSite=Strict로 보호 |
| CORS | Spring Security CorsConfigurationSource — 프론트엔드 Origin만 허용 |
| 보안 헤더 | Spring Security 기본 헤더 + `X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security` |
| 입력 검증 | `jakarta.validation` Bean Validation, `@Valid` Controller 적용 |
| SQL Injection | MyBatis `#{}` 파라미터 바인딩 (PreparedStatement), `${}` 사용 금지 |
| 감사로그 | AOP `@Around` — 모든 Service 메서드 진입·종료 시 사용자 ID, IP, 메서드명, 결과 기록 |
| 접근성 (KWCAG) | Playwright E2E에 axe-core 통합, 자동화 검사로 CI 차단 |

---

## 5. 빌드 도구

### Gradle 8 (권장)

**권장 이유:** 증분 빌드, 빌드 캐시, 병렬 실행으로 Maven 대비 빌드 속도 30~50% 향상. Version Catalog(`libs.versions.toml`)로 버전 중앙화. Kotlin DSL(`build.gradle.kts`) 지원으로 IDE 자동완성.

```
backend/
├── build.gradle.kts          # Kotlin DSL 빌드 스크립트
├── settings.gradle.kts
└── gradle/
    └── libs.versions.toml    # [versions], [libraries], [plugins] 섹션
```

멀티 프로젝트 구조: 현재는 단일 모듈이지만 `settings.gradle.kts`에서 하위 모듈 추가 가능 (예: `include(":common")`, `include(":domain-auth")`).

### Maven 3.9+ (대안)

표준 `pom.xml` 구조. BOM 임포트로 버전 일관성 유지:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>3.2.x</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

egovframe 공식 샘플이 Maven 기반이므로 Maven 선택 시 공통컴포넌트 의존성 참조가 더 직관적.

---

## 6. 컨테이너

### Dockerfile 전략

**Dockerfile.backend** (Multi-stage):

```
Stage 1 (builder): eclipse-temurin:17-jdk-alpine + Gradle/Maven 빌드
Stage 2 (runtime): eclipse-temurin:17-jre-alpine — 빌드 산출물만 복사
```

**Dockerfile.admin / Dockerfile.public** (Multi-stage):

```
Stage 1 (builder): node:20-alpine + pnpm install + vite build
Stage 2 (runtime): nginx:alpine — dist/ 정적 파일 serve
```

### docker-compose.yml 서비스 구성

| 서비스 | 이미지 | 포트 | 역할 |
|--------|--------|------|------|
| postgres | postgres:16-alpine | 5432 | 개발·테스트 DB |
| backend | ./deploy/Dockerfile.backend | 8080 | Spring Boot API 서버 |
| admin-fe | ./deploy/Dockerfile.admin | 3001 | 관리자 SPA |
| public-fe | ./deploy/Dockerfile.public | 3000 | 공공 웹사이트 SPA |
| nginx | nginx:alpine | 80, 443 | 리버스 프록시, SSL 종료 |

개발 환경: `docker-compose.yml` (핫리로드용 볼륨 마운트 포함)
운영 환경: `docker-compose.prod.yml` (리소스 제한, 환경변수 시크릿 주입)

---

## 7. CI/CD

### GitHub Actions 잡 구성 — 초안, 검토 필요

**ci-backend.yml** (트리거: `backend/**` 경로 변경):

```
1. checkout
2. Java 17 셋업 (temurin)
3. Gradle 캐시
4. ./gradlew test (Testcontainers 포함)
5. ./gradlew jacocoTestReport
6. 커버리지 85% 미달 시 실패
7. (선택) SonarQube 분석
8. Docker 이미지 빌드 + 레지스트리 Push
```

**ci-frontend.yml** (트리거: `frontend/**` 경로 변경):

```
1. checkout
2. Node 20 + pnpm 셋업
3. pnpm install
4. vitest --coverage (커버리지 85%)
5. Playwright E2E (axe-core 접근성 검사 포함)
6. vite build
7. Docker 이미지 빌드 + Push
```

**cd-deploy.yml** (트리거: main 브랜치 merge):

```
1. 환경별 docker-compose 배포 (dev → 자동, prod → 수동 승인)
```

---

## 8. 관측성

| 항목 | 기술 | 설명 |
|------|------|------|
| 헬스 체크 | Spring Boot Actuator | `/actuator/health`, `/actuator/info` 엔드포인트 |
| 메트릭 | Micrometer + Prometheus | `/actuator/prometheus` — Grafana 연동 가능 |
| 로그 포맷 | Logback JSON (운영) / 콘솔 (개발) | 구조화 로그, ELK 스택 연동 준비 |
| 분산 추적 | OpenTelemetry (선택) | OTLP 엑스포터 설정 시 Jaeger/Zipkin 연동 |
| 감사로그 | AOP 기반 DB 저장 | 모든 데이터 변경 이력 `audit_log` 테이블 기록 |

운영 환경에서 `/actuator/` 하위 엔드포인트는 내부망만 노출 (nginx location 블록으로 제한).

---

## 9. 의존성 버전 표

| 라이브러리 | 버전 |
|-----------|------|
| OpenJDK | 17 LTS |
| Spring Boot | 3.5.9 |
| Spring Framework | 6.x |
| Spring Security | 6.x |
| egovframe | 5.0.0 |
| MyBatis Spring Boot Starter | 3.0.4 |
| PostgreSQL JDBC Driver | 42.7.x |
| Flyway | 10.x |
| HikariCP | Spring Boot 내장 |
| springdoc-openapi-starter-webmvc-ui | 2.8.17 |
| jjwt (JWT) | 0.12.7 |
| jsoup | 1.17.2 |
| JaCoCo | 0.8.13 |
| JUnit 5 | Spring Boot 내장 |
| Mockito | Spring Boot 내장 |
| Testcontainers | 1.20.4 |
| Gradle | 8.x |
| Node.js | 20 LTS |
| Vue | 3.5+ |
| TypeScript | 5.x |
| Vite | 6 |
| Pinia | 2.x |
| Vue Router | 4.x |
| Element Plus | 2.8+ |
| Tailwind CSS | 3.x |
| vue-i18n | 9.x |
| Axios | 1.x |
| Vitest | 1.x |
| Vue Test Utils | 2.x |
| Playwright | 1.x |
| DOMPurify | ^3.1.6 |
| @types/dompurify | ^3.0.5 |
| Docker | 24+ |
| nginx | alpine (latest stable) |

---

## 10. 결정 로그 (Decision Log)

### DR-001: Vue 선택 이유 (React 대신)

- **결정:** Vue 3 채택
- **근거:** Element Plus 생태계가 공공기관 백오피스 UI(테이블, 폼, 트리 위젯)에 즉시 활용 가능. Composition API의 코드 조직화 방식이 egovframe 백엔드 레이어 구조와 자연스럽게 대응. 팀 내 Vue 경험 선호.
- **트레이드오프:** React 생태계 대비 커뮤니티 규모 소폭 작음. 전략적으로 감수 가능.

### DR-002: Gradle 권장 이유 (Maven 대신)

- **결정:** Gradle 8 권장, Maven 대안으로 유지
- **근거:** 증분 빌드·병렬 실행으로 CI 빌드 시간 단축. Version Catalog(`libs.versions.toml`)로 버전 일원 관리. Kotlin DSL IDE 자동완성.
- **트레이드오프:** egovframe 공식 예제가 Maven 기반이므로 Maven 선택 가능. 두 옵션 모두 지원 문서화.

### DR-003: MyBatis 선택 이유 (JPA/Hibernate 대신)

- **결정:** MyBatis 3.5.x 채택
- **근거:** egovframe 공통컴포넌트 SQL Mapper 표준이 MyBatis 기반. 복잡한 공공기관 조회 쿼리(통계, 다중 조인)를 XML에서 직접 제어하는 것이 유지보수에 유리.
- **트레이드오프:** JPA 대비 보일러플레이트 증가. 공통컴포넌트 표준 준수 우선.

### DR-004: PostgreSQL 선택 이유 (Oracle 대신)

- **결정:** PostgreSQL 16 채택
- **근거:** 오픈소스, 라이선스 비용 없음, JSONB 지원, 공공기관 오픈소스 전환 추세에 부합.
- **트레이드오프:** 공통컴포넌트 DDL을 Oracle → PostgreSQL 변환 필요 (tech.md §3 참조). 변환 비용은 일회성.

### DR-005: 모노레포 선택 이유

- **결정:** 단일 저장소 (backend/ + frontend/)
- **근거:** 백·프론트 계약(OpenAPI 스키마) 단일 PR 검증, 공유 타입 관리, CI/CD 단순화.
- **트레이드오프:** 저장소 크기 증가. 경로 기반 CI 트리거로 불필요한 잡 실행 방지.

---

_문서 버전: v0.3 (OWASP 보안 감사 — jsoup 1.17.2 + DOMPurify ^3.1.6 추가 — 2026-05-15)_
_작성일: 2026-04-29_
_스택 변경 시: 이 문서 §9 버전 표와 structure.md §6 동시 업데이트 필요_
