# iroum-cms 구조 문서

> 상세 요구사항은 SPEC-CMS-001 참조 (작성 예정)

---

## 1. 저장소 구조 결정

**채택: 단일 저장소 모노레포 (Monorepo)**

백엔드(`backend/`)와 프론트엔드(`frontend/`)를 하나의 Git 저장소 안에서 하위 폴더로 분리하는 구조를 선택합니다.

**선택 이유:**

- 공통컴포넌트 DDL 변환, API 타입 정의, i18n 리소스 등 공유 자산의 일원 관리
- 프론트·백 사이 계약(OpenAPI 3.1 스키마)을 단일 PR에서 원자적으로 검증 가능
- egovframe 백엔드와 Vue SPA 간 버전 동기화가 명확
- GitHub Actions 워크플로우를 단일 `.github/workflows/` 에서 통합 관리

**트레이드오프 인식:**

- 저장소 크기가 증가하므로 `.gitignore`와 스파스 체크아웃 정책 필요
- 백·프론트 CI 잡을 경로 기반 트리거(`paths:`)로 독립 실행 필요

---

## 2. 디렉터리 구조

```
iroum-cms/
├── backend/                              # Spring Boot 3.2 + egovframe 5.0
│   ├── build.gradle (또는 pom.xml)
│   ├── settings.gradle
│   ├── gradle/libs.versions.toml          # Version Catalog (Gradle 권장)
│   └── src/
│       ├── main/
│       │   ├── java/kr/co/ircp/cms/
│       │   │   ├── IroumCmsApplication.java
│       │   │   ├── config/
│       │   │   │   ├── SecurityConfig.java
│       │   │   │   ├── JwtConfig.java
│       │   │   │   ├── SwaggerConfig.java
│       │   │   │   ├── CorsConfig.java
│       │   │   │   ├── I18nConfig.java
│       │   │   │   └── MyBatisConfig.java
│       │   │   ├── common/
│       │   │   │   ├── exception/         # 글로벌 예외 처리
│       │   │   │   ├── response/          # 공통 응답 포맷 (ApiResponse<T>)
│       │   │   │   ├── util/              # 공통 유틸리티
│       │   │   │   ├── interceptor/       # 로그인 체크, 권한 인터셉터
│       │   │   │   └── aop/               # 감사로그(Audit Log) AOP
│       │   │   ├── domain/
│       │   │   │   ├── auth/              # A묶음: 회원·권한·로그인
│       │   │   │   │   ├── user/          # uss/umt 사용자관리
│       │   │   │   │   ├── role/          # sec/rmt 권한관리, sec/aut 역할관리
│       │   │   │   │   └── login/         # uat/uia 로그인·JWT
│       │   │   │   ├── board/             # B묶음: 게시판·공지·Q&A·FAQ
│       │   │   │   │   ├── bbs/           # cop/bbs 게시판
│       │   │   │   │   ├── notice/        # cop/ntc 공지사항
│       │   │   │   │   ├── faq/           # cop/com/faq
│       │   │   │   │   ├── qna/           # cop/com/qna
│       │   │   │   │   └── file/          # cop/cmm/fms 첨부파일
│       │   │   │   ├── content/           # C묶음: 콘텐츠·메뉴·사이트관리
│       │   │   │   │   ├── menu/          # sym/mnu 메뉴관리
│       │   │   │   │   ├── template/      # uss/ion/tmm 템플릿관리
│       │   │   │   │   ├── site/          # 사이트관리
│       │   │   │   │   ├── page/          # 페이지·콘텐츠관리
│       │   │   │   │   └── banner/        # 팝업·배너관리
│       │   │   │   └── system/            # D묶음: 통계·로그·시스템관리
│       │   │   │       ├── log/           # sym/log 접속로그·통계
│       │   │   │       ├── code/          # sym/ccm 코드관리
│       │   │   │       └── dashboard/     # 시스템 운영 대시보드
│       │   │   └── infra/
│       │   │       ├── persistence/       # MyBatis Mapper 인터페이스
│       │   │       └── security/          # JWT 필터, UserDetails 구현체
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-local.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       ├── mapper/                # MyBatis XML Mapper
│       │       │   ├── auth/
│       │       │   ├── board/
│       │       │   ├── content/
│       │       │   └── system/
│       │       ├── messages/              # i18n 메시지
│       │       │   ├── messages_ko.properties
│       │       │   └── messages_en.properties
│       │       └── db/migration/          # Flyway SQL 마이그레이션
│       │           ├── V1__init_schema.sql
│       │           └── V2__seed_codes.sql
│       └── test/
│           └── java/kr/co/ircp/cms/
│               ├── domain/               # 도메인별 단위 테스트
│               └── integration/          # Testcontainers 통합 테스트
├── frontend/
│   ├── admin/                            # Vue 3 SPA — 관리자 백오피스
│   │   ├── index.html
│   │   ├── vite.config.ts
│   │   ├── tsconfig.json
│   │   ├── package.json
│   │   └── src/
│   │       ├── main.ts
│   │       ├── App.vue
│   │       ├── router/
│   │       │   └── index.ts              # Vue Router 4 라우팅 정의
│   │       ├── stores/                   # Pinia 스토어
│   │       │   ├── auth.ts
│   │       │   └── ui.ts
│   │       ├── views/                    # 라우트 단위 페이지 컴포넌트
│   │       │   ├── auth/
│   │       │   ├── board/
│   │       │   ├── content/
│   │       │   └── system/
│   │       ├── components/               # 재사용 컴포넌트
│   │       │   ├── common/
│   │       │   ├── layout/
│   │       │   └── form/
│   │       ├── composables/              # Vue Composables (공통 로직)
│   │       ├── api/                      # Axios 인스턴스 + OpenAPI 생성 클라이언트
│   │       │   ├── client.ts
│   │       │   └── generated/           # openapi-typescript-codegen 출력
│   │       ├── locales/                  # vue-i18n 리소스
│   │       │   ├── ko.json
│   │       │   └── en.json
│   │       └── assets/
│   ├── public/                           # Vue 3 SPA — 공공 웹사이트
│   │   ├── vite.config.ts
│   │   ├── package.json
│   │   └── src/                         # admin과 동일 구조 (공개 페이지 특화)
│   │       ├── views/
│   │       │   ├── home/
│   │       │   ├── board/               # 공개 게시판·공지 조회
│   │       │   └── content/             # 공개 페이지 렌더링
│   │       └── ...
│   └── shared/                          # 두 SPA 공유 타입·유틸
│       ├── types/                        # TypeScript 공통 타입 정의
│       └── utils/                        # 날짜, 포맷터, 검증 등 순수 유틸
├── deploy/
│   ├── Dockerfile.backend                # Multi-stage: build(JDK17) → runtime(JRE17)
│   ├── Dockerfile.admin                  # Multi-stage: build(Node) → serve(nginx)
│   ├── Dockerfile.public                 # Multi-stage: build(Node) → serve(nginx)
│   ├── docker-compose.yml               # 로컬 개발 전체 스택
│   ├── docker-compose.prod.yml          # 운영 오버라이드
│   └── nginx/
│       ├── nginx.conf
│       └── conf.d/
│           ├── admin.conf
│           └── public.conf
├── docs/
│   ├── adr/                             # Architecture Decision Records
│   └── api/                             # OpenAPI 스펙 (springdoc 생성)
├── .github/
│   └── workflows/
│       ├── ci-backend.yml
│       ├── ci-frontend.yml
│       └── cd-deploy.yml
├── .moai/                               # MoAI 메타데이터 (기존 유지)
├── .gitignore
├── README.md
└── LICENSE
```

---

## 3. 도메인 모듈 분리 원칙

4개 공통컴포넌트 묶음을 1차 패키지 분할 기준으로 사용합니다.

| 묶음 | 백엔드 패키지 | 프론트 views/ | 책임 |
|------|-------------|--------------|------|
| A. 회원·권한·로그인 | `domain.auth` | `views/auth/` | 인증·인가 전담, 타 묶음의 보안 기반 제공 |
| B. 게시판·공지·Q&A·FAQ | `domain.board` | `views/board/` | 콘텐츠 등록·조회·첨부파일 처리 |
| C. 콘텐츠·메뉴·사이트관리 | `domain.content` | `views/content/` | 사이트 구조·템플릿·페이지 렌더링 |
| D. 통계·로그·시스템관리 | `domain.system` | `views/system/` | 운영 모니터링, 코드 관리, 대시보드 |

**모듈 경계 원칙:**

- 각 도메인 패키지는 자체 Controller, Service, Mapper(Dao), VO/DTO를 보유
- 도메인 간 직접 참조 금지: 공통 기능은 `common/` 패키지를 통해서만 공유
- A묶음(auth)은 B/C/D에서 의존하지만, B/C/D는 A를 직접 호출하지 않음 (Spring Security 컨텍스트 활용)
- 감사로그 AOP(`common/aop/AuditLogAspect.java`)가 모든 Service 레이어를 횡단 관심사로 처리

---

## 4. 명명 규약

### 백엔드 (Java)

| 레이어 | 클래스 접미사 | 예시 |
|--------|-------------|------|
| REST Controller | `Controller` | `BoardController.java` |
| 서비스 인터페이스 | `Service` | `BoardService.java` |
| 서비스 구현체 | `ServiceImpl` | `BoardServiceImpl.java` |
| MyBatis Mapper 인터페이스 | `Mapper` | `BoardMapper.java` |
| 요청 DTO | `Request` | `BoardCreateRequest.java` |
| 응답 DTO / VO | `Response` / `VO` | `BoardResponse.java` |
| 도메인 모델 | (접미사 없음) | `Board.java` |

- 기본 패키지: `kr.co.ircp.cms`
- 도메인 서브패키지: `kr.co.ircp.cms.domain.{묶음}.{기능}`
- 설정 클래스: `kr.co.ircp.cms.config`

### 프론트엔드 (Vue + TypeScript)

| 항목 | 규약 | 예시 |
|------|------|------|
| 파일명 (컴포넌트) | PascalCase + `.vue` | `BoardList.vue` |
| 파일명 (유틸/스토어/라우터) | kebab-case + `.ts` | `board-api.ts` |
| 컴포넌트 이름 | PascalCase | `<BoardList />` |
| Pinia 스토어 파일 | kebab-case + `Store` | `useAuthStore` (파일: `auth.ts`) |
| Composable | `use` 접두사 | `useBoardForm.ts` |

### REST API URL

```
/api/v1/{domain}/{resource}
/api/v1/{domain}/{resource}/{id}
/api/v1/{domain}/{resource}/{id}/{action}
```

예시:
- `GET  /api/v1/board/posts`
- `POST /api/v1/board/posts`
- `GET  /api/v1/board/posts/{id}`
- `POST /api/v1/auth/login`
- `GET  /api/v1/system/codes/{groupCode}`

---

## 5. 환경별 설정

| 환경 | 프로파일 | 특징 |
|------|----------|------|
| local | `application-local.yml` | docker-compose 기반, H2 또는 로컬 PostgreSQL, 디버그 로그, Swagger UI 활성화 |
| dev | `application-dev.yml` | 개발 서버 PostgreSQL, 실제 데이터, Swagger UI 활성화 |
| prod | `application-prod.yml` | PostgreSQL 운영 DB, 로그 JSON 포맷, Swagger UI 비활성화, 보안 헤더 강화 |

환경 활성화:
- Spring: `-Dspring.profiles.active={profile}` 또는 환경변수 `SPRING_PROFILES_ACTIVE`
- Vite(FE): `.env.local`, `.env.development`, `.env.production` 파일로 분리

**민감 정보 처리:**
- DB 접속 정보, JWT 시크릿키, 외부 API 키는 환경변수로만 주입 (application-prod.yml에 하드코딩 금지)
- `.env.production`은 Git 추적 대상 제외 (`.gitignore` 등록)

---

## 6. 의존성 관리

### 백엔드 (Gradle 권장)

```
backend/
├── build.gradle            # 루트 빌드 스크립트
├── settings.gradle         # 멀티 프로젝트 설정 (추후 모듈 분리 가능)
└── gradle/
    └── libs.versions.toml  # Version Catalog — 모든 라이브러리 버전 중앙 관리
```

`libs.versions.toml` 예시 구조:

```toml
[versions]
spring-boot = "3.2.x"
egovframe = "5.0.0"
mybatis-spring = "3.0.x"
postgresql = "42.7.x"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web", version.ref = "spring-boot" }
```

Maven 대안: `pom.xml` `<dependencyManagement>` 섹션에 BOM 임포트로 동일 효과 달성 가능. 상세 내용은 tech.md 참조.

### 프론트엔드

```
frontend/
├── admin/package.json      # 관리자 SPA 의존성
├── public/package.json     # 공공 SPA 의존성
└── shared/package.json     # 공유 타입·유틸 패키지
```

- 패키지 매니저: pnpm (workspaces 기능으로 모노레포 관리) — 초안, 검토 필요
- `frontend/pnpm-workspace.yaml` 에서 세 패키지를 workspace로 선언
- 공유 패키지는 `@iroum-cms/shared` 로컬 패키지명으로 참조

---

_문서 버전: 초안 v0.1_
_작성일: 2026-04-29_
_다음 단계: SPEC-CMS-001 작성 후 모듈 경계 및 API 계약 상세화 권장_
