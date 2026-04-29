# iroum-cms 백엔드

egovFrame 5.0 + Spring Boot 3.5 기반 CMS API 서버 (Step 0 — Bootstrap)

---

## 기술 스택

| 레이어 | 기술 | 버전 |
|--------|------|------|
| JDK | Eclipse Temurin (Adoptium) | 17 LTS (Gradle toolchain 자동 다운로드) |
| 빌드 도구 | Gradle | 8.10 |
| 프레임워크 | Spring Boot | 3.5.9 |
| 표준 프레임워크 | egovFrame | 5.0.0 (SPEC-CMS-002에서 통합 예정) |
| ORM | MyBatis Spring Boot Starter | 3.0.4 |
| DB | PostgreSQL | 16 |
| DB 마이그레이션 | Flyway | 10.x |
| 인증 | Spring Security + jjwt | 6.x + 0.12.7 |
| API 문서 | springdoc-openapi | 2.8.17 |
| 테스트 | JUnit 5 + Mockito + Testcontainers | Spring Boot 내장 + 1.20.4 |

---

## 사전 준비

- **Docker**: Testcontainers가 실행 중인 Docker 데몬을 필요로 한다.
- **Java (호스트)**: Gradle wrapper 실행에 필요. JDK 8 이상이면 충분.
  Java 17은 Gradle toolchain이 [Foojay API](https://foojay.io/)를 통해 자동 다운로드하므로
  별도 설치가 불필요하다.
- **인터넷 연결**: Gradle 배포 파일 및 Maven 의존성 다운로드에 필요.

---

## Gradle Wrapper 초기화 (최초 1회)

저장소를 클론하면 `gradle/wrapper/gradle-wrapper.properties`만 존재하고
바이너리(`gradle-wrapper.jar`)와 `gradlew` 스크립트는 없다.

아래 방법 중 하나로 초기화한다.

### 방법 A — setup-wrapper.sh 실행 (권장)

```bash
cd backend
bash setup-wrapper.sh
```

스크립트가 Gradle 8.10 배포 파일을 다운로드하고 `gradlew`와 `gradlew.bat`을 생성한다.

### 방법 B — 시스템 Gradle로 직접 생성

시스템에 Gradle 8.x가 설치되어 있는 경우:

```bash
cd backend
gradle wrapper --gradle-version 8.10 --distribution-type bin
```

---

## 빌드

```bash
cd backend
./gradlew build
```

`build/libs/iroum-cms.jar` 가 생성된다.

---

## 실행 (로컬 개발)

로컬 PostgreSQL이 `localhost:5432`에서 실행 중이어야 한다.
`docker-compose.yml`이 준비되면 `docker-compose up postgres`로 실행 가능하다.

```bash
./gradlew bootRun -Pspring-boot.run.profiles=local
```

또는 환경변수로 프로파일 선택:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

---

## 테스트

```bash
./gradlew test jacocoTestReport
```

- **단위 테스트** (`HealthControllerTest`): Docker 불필요, WebMvcTest 슬라이스
- **통합 테스트** (`IroumCmsApplicationTests`): Docker 필요, Testcontainers 자동 구동

JaCoCo 리포트 위치: `build/reports/jacoco/test/html/index.html`

---

## 헬스 체크 확인

```bash
curl http://localhost:8080/api/v1/health
```

기대 응답:

```json
{
  "status": "UP",
  "service": "iroum-cms-backend",
  "version": "0.1.0-SNAPSHOT"
}
```

---

## API 문서 (Swagger UI)

로컬 실행 후: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

OpenAPI 스펙 JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

> 운영 환경(`application-prod.yml`)에서는 Swagger UI가 비활성화된다.

---

## egovFrame 5.0 통합 현황

현재 Step 0 bootstrap 단계로, egovFrame 의존성은 `build.gradle.kts`에 주석 처리되어 있다.

```
// TODO(SPEC-CMS-002): egovFrame Maven 저장소(https://maven.egovframe.go.kr/maven/) 접근 확인 후 주석 해제
```

SPEC-CMS-002 구현 단계에서 저장소 접근이 가능하다면 주석을 해제한다.
접근이 불가하면 배포된 JAR을 `libs/` 폴더에 직접 배치하는 방법을 사용한다.

---

## 환경 프로파일

| 프로파일 | 활성화 방법 | 특징 |
|----------|------------|------|
| `local` | 기본값 | 로컬 PostgreSQL, 디버그 로그, Swagger UI 활성화 |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | 환경변수 기반 DB 설정, JSON 로그, Swagger UI 비활성화 |

---

---

## SPEC-CMS-002 Step 1 — TDD RED 단계

**현재 상태**: SPEC-CMS-002 REQ-AUTH-001~005, 011 RED 단계 진입 완료 (2026-04-29)

### RED 단계 정의

모든 인증 관련 테스트는 실행 시 **의도적으로 실패**한다.
서비스 구현체(`AuthServiceImpl`, `JwtTokenProviderImpl`, `PasswordPolicyServiceImpl`)는
`UnsupportedOperationException("RED — Step 2 GREEN에서 구현")`을 던진다.

컴파일은 성공하고, 기존 Health 테스트와 ApplicationTests는 정상 PASS된다.

### 테스트 실행

```bash
cd backend
./gradlew test
```

예상 결과:
- `JwtTokenProviderTest` — **전체 실패** (RED 의도: UOE)
- `PasswordPolicyServiceTest` — **전체 실패** (RED 의도: UOE)
- `AuthServiceTest` — **전체 실패** (RED 의도: UOE)
- `AuthControllerTest` — **전체 실패** (RED 의도: UOE → HTTP 500)
- `HealthControllerTest` — **PASS** (변경 없음)
- `IroumCmsApplicationTests` — **PASS** (변경 없음)

### RED 범위 파일 목록

| 레이어 | 파일 수 |
|--------|---------|
| Entity | 6 (User, UserStatus, Role, RefreshToken, LoginHistory, TokenBlacklist) |
| DTO | 3 (LoginRequest, LoginResponse, RefreshResult) |
| Exception | 6 (AuthException + 5 서브클래스) |
| Repository (Mapper) | 4 (UserMapper, RefreshTokenMapper, LoginHistoryMapper, TokenBlacklistMapper) |
| Service (인터페이스+구현) | 6 (JwtTokenProvider/Impl, PasswordPolicyService/Impl, AuthService/Impl) |
| Controller | 1 (AuthController) |
| Config | 1 (JwtProperties) |
| Flyway Migration | 1 (V2__auth_schema.sql) |
| MyBatis XML | 4 (auth/*.xml) |
| Test | 4 (Jwt/Password/Auth/Controller 테스트) |

### 다음 단계 (Step 2 GREEN)

`UnsupportedOperationException`을 실제 구현으로 교체:
1. `JwtTokenProviderImpl` — jjwt 0.12.6으로 JWT 생성/검증
2. `PasswordPolicyServiceImpl` — BCrypt strength=12 + 8자/3종 정책
3. `AuthServiceImpl` — 로그인·토큰 갱신·로그아웃 전체 흐름

---

_Step 1 (RED) — SPEC-CMS-002 | iroum-cms v0.1.0-SNAPSHOT_
