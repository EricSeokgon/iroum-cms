# SPEC-CMS-001: 공공기관 CMS — 1차 출시 기반(Umbrella SPEC)

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-001 |
| 제목 | Public-Institution CMS — First Release Foundation |
| 작성일 | 2026-04-29 |
| 버전 | v0.3 (2026-04-29 RFP 통합 + 홍익인간 CMS gap 분석 통합) |
| 작성자 | manager-spec (MoAI) |
| 상태 | Draft |
| 우선순위 | P0 |
| 분류 | Umbrella SPEC (후속 SPEC-CMS-002 ~ SPEC-CMS-010 + 옵션 트랙 분할) |
| 참조 문서 | `.moai/project/product.md`, `.moai/project/structure.md`, `.moai/project/tech.md` |

본 SPEC은 iroum-cms 1차 출시(공공기관 CMS) 전체 범위를 정의하는 **상위 우산형(Umbrella) SPEC**이다. egovframe 5.0.0 + 공통컴포넌트 v5의 백엔드 로직을 차용하되, 화면(60~80 SPA pages)은 Vue 3 + Element Plus로 신규 구축한다.

---

## 2. 배경 및 동기

공공기관 웹사이트는 전자정부 표준프레임워크(egovframe) 준수가 의무이며, 동시에 KWCAG 2.2 AA 접근성·개인정보보호법(PIA)·감사로그 보존 등 다양한 법적 요구를 충족해야 한다. 그러나 기존 공통컴포넌트의 표준 화면(JSP 기반)은 운영자 UX·반응형·다국어 측면에서 한계가 있다.

iroum-cms는 다음을 목표로 설계된다.

1. **표준 준수**: egovframe v5 + 공통컴포넌트 v5의 백엔드 로직(엔티티·SQL·비즈니스 규칙)을 충실히 차용한다.
2. **현대적 SPA**: 모든 화면을 Vue 3 + TypeScript + Element Plus로 신규 구축하여 운영자 친화성과 시민 접근성을 확보한다.
3. **법적 의무 자동화**: KWCAG 2.2 AA, PIA, 감사로그를 CI 파이프라인에 통합하여 인적 누락을 방지한다.

---

## 3. 목표 및 비목표

### 3.1 1차 출시 목표

- 4개 공통컴포넌트 묶음(A: 회원·권한·로그인 / B: 게시판·공지·Q&A·FAQ / C: 콘텐츠·메뉴·사이트 / D: 통계·로그·시스템) 신규 구축
- 관리자 백오피스 SPA(40~50 pages) + 공공 웹사이트 SPA(20~30 pages) 분리
- JWT 기반 인증·인가, 메뉴별 권한, 감사로그 AOP, 다국어(한/영) 지원
- Docker 컨테이너 배포 + GitHub Actions CI/CD
- 테스트 커버리지 85% 이상(JaCoCo + Vitest), KWCAG 2.2 AA 자동 검사 0건 실패

### 3.2 비목표 (Out of Scope, 후속 SPEC 대상)

| 항목 | 사유 |
|------|------|
| 소셜 로그인 (카카오·네이버) | 공공기관 보안 정책 별도 검토 |
| GPKI / 공동인증서 연동 | 외부 라이브러리·법적 검토 필요 |
| 모바일 네이티브 앱 (iOS/Android) | 1차는 반응형 웹으로 대응 |
| AI 기반 콘텐츠 추천·자동 생성 | 도입 절차 별도 |
| 전자결재·그룹웨어 연동 | 기관별 시스템 상이 |
| Oracle / MySQL / 다중 DB 지원 | 1차는 PostgreSQL 단일 |
| 멀티테넌시 (SaaS형 다기관 운영) | 아키텍처 복잡도 |

---

## 4. 이해관계자 및 페르소나

| 페르소나 | 역할 | 핵심 요구 |
|----------|------|----------|
| 콘텐츠 관리자 | 기관 웹사이트 콘텐츠 작성·발행 | 직관적 에디터, 빠른 발행 워크플로우 |
| 시스템 운영자 | 사용자·권한·메뉴·코드 관리, 감사로그 점검 | 명확한 권한 매트릭스, 통합 운영 대시보드 |
| 보안 담당자 | PIA·접근성·감사로그 검토 | 자동화된 컴플라이언스 리포트 |
| 일반 시민 | 공지사항·정보 조회 | 빠른 로딩, 스크린리더 호환, 모바일 친화 |

---

## 5. 시스템 컨텍스트 다이어그램

```
                ┌──────────────────────┐
                │  일반 시민 (브라우저) │
                └──────────┬───────────┘
                           │ HTTPS
                           ▼
┌─────────────────────────────────────────────────┐
│   nginx (리버스 프록시 / SSL 종료 / 정적 서빙)    │
└──────────┬─────────────────────────┬────────────┘
           │                         │
           ▼                         ▼
  ┌────────────────┐       ┌──────────────────┐
  │ Public SPA     │       │ Admin SPA        │
  │ Vue 3 + EP     │       │ Vue 3 + EP       │
  │ (시민용 20~30p)│       │ (운영자 40~50p)  │
  └────────┬───────┘       └────────┬─────────┘
           │ /api/v1/*              │ /api/v1/*
           └─────────────┬──────────┘
                         ▼
            ┌────────────────────────────┐
            │ Spring Boot 3.2 + egovframe│
            │ JWT Filter Chain           │
            │ 4 Domain Bundles (A/B/C/D) │
            │ AuditLog AOP               │
            └────┬───────────────────────┘
                 │ MyBatis 3.5
                 ▼
       ┌──────────────────────┐
       │ PostgreSQL 16        │
       │ (audit_log, users,   │
       │  bbs, content, ...)  │
       └──────────────────────┘
```

---

## 6. 요구사항 (EARS 형식)

### 6.0 EARS 형식 가이드

> **EARS 4가지 핵심 패턴**
>
> - **Ubiquitous (편재형):** 시스템은 ~해야 한다
> - **Event-driven (이벤트 기반):** \<이벤트\>가 발생했을 때, 시스템은 ~해야 한다
> - **State-driven (상태 기반):** \<상태\>인 동안, 시스템은 ~해야 한다
> - **Optional (선택형):** \<기능이 활성화된 경우\>, 시스템은 ~해야 한다

### 6.1 REQ-AUTH-* : 회원·권한·로그인 (Bundle A)

- **REQ-AUTH-001 (사용자 로그인 — Event-driven)**
  사용자가 ID와 비밀번호를 제출하여 `POST /api/v1/auth/login` 을 호출했을 때, 시스템은 자격증명을 BCrypt로 검증하고, 성공 시 Access Token(JWT, 만료 15분)을 응답 본문으로, Refresh Token(만료 7일)을 HttpOnly·Secure·SameSite=Strict 쿠키로 발급해야 한다.

- **REQ-AUTH-002 (Access Token 갱신 — Event-driven)**
  클라이언트가 `POST /api/v1/auth/refresh` 를 호출했을 때, 시스템은 HttpOnly Cookie의 Refresh Token을 검증하고, 유효하면 새 Access Token을 발급해야 하며 Refresh Token Rotation 정책에 따라 Refresh Token도 재발급해야 한다.

- **REQ-AUTH-003 (로그아웃 — Event-driven)**
  사용자가 `POST /api/v1/auth/logout` 을 호출했을 때, 시스템은 Refresh Token을 무효화 목록(token_blacklist)에 등록하고, 클라이언트의 Refresh Cookie를 만료시켜야 한다.

- **REQ-AUTH-004 (비밀번호 정책 — Ubiquitous)**
  시스템은 비밀번호를 8자 이상, 대소문자·숫자·특수문자 중 3종 이상 조합으로 강제해야 하며, BCrypt(strength=12)로 해싱하여 저장해야 한다. (REQ-CROSS-002 의존)

- **REQ-AUTH-005 (계정 잠금 — Event-driven)**
  로그인 실패가 5회 연속 발생했을 때, 시스템은 해당 계정을 30분간 잠금 상태로 전환하고 login_history 테이블에 잠금 사유를 기록해야 한다.

- **REQ-AUTH-006 (사용자 CRUD — Ubiquitous)**
  시스템은 운영자가 `/api/v1/auth/users` 엔드포인트를 통해 사용자 계정의 생성·조회·수정·비활성화를 수행할 수 있도록 제공해야 한다. (egovframe uss/umt 차용)

- **REQ-AUTH-007 (역할 관리 — Ubiquitous)**
  시스템은 역할(Role)을 정의하고 사용자에게 다대다로 매핑하며, 역할별로 접근 가능한 권한(Permission) 집합을 관리할 수 있어야 한다. (egovframe sec/aut 차용)

- **REQ-AUTH-008 (메뉴별 권한 검사 — State-driven)**
  사용자가 보호된 API를 호출하는 동안, 시스템은 Spring Security `@PreAuthorize` 와 menu_permission 매핑 테이블을 조회하여, 해당 사용자의 역할이 메뉴 권한을 보유하는지 확인하고 미보유 시 HTTP 403을 반환해야 한다.

- **REQ-AUTH-009 (비밀번호 변경 — Event-driven)**
  사용자가 비밀번호 변경을 요청했을 때, 시스템은 현재 비밀번호 검증 후 새 비밀번호를 적용하고, 변경 이력을 password_history 테이블에 기록해야 한다.

- **REQ-AUTH-010 (비밀번호 재사용 금지 — Ubiquitous)**
  시스템은 비밀번호 변경 시 직전 3회 사용한 비밀번호와 동일한 값을 거부해야 한다.

- **REQ-AUTH-011 (로그인 이력 기록 — Ubiquitous)**
  시스템은 모든 로그인 시도(성공·실패)에 대해 사용자 ID, IP, User-Agent, 시각, 결과를 login_history 테이블에 기록해야 한다.

- **REQ-AUTH-012 (관리자 강제 로그아웃 — Optional)**
  관리자가 강제 로그아웃 기능을 활성화한 경우, 시스템은 특정 사용자의 모든 활성 Refresh Token을 즉시 무효화할 수 있어야 한다.

### 6.2 REQ-BOARD-* : 게시판·공지·Q&A·FAQ (Bundle B)

- **REQ-BOARD-001 (게시판 마스터 정의 — Ubiquitous)**
  시스템은 운영자가 게시판 마스터(이름·유형·페이징 크기·댓글 허용 여부·첨부파일 허용 여부·권한)를 정의할 수 있도록 `/api/v1/board/masters` 엔드포인트를 제공해야 한다. (egovframe cop/bbs 차용)

- **REQ-BOARD-002 (게시글 CRUD — Ubiquitous)**
  시스템은 게시글의 생성·조회·수정·삭제(논리 삭제)를 `/api/v1/board/posts` 로 제공하며, 페이징(`?page=&size=&sort=`)과 검색(제목·본문·작성자)을 지원해야 한다.

- **REQ-BOARD-003 (댓글 — Optional)**
  게시판 마스터에서 댓글이 허용된 경우, 시스템은 게시글에 대한 댓글 작성·수정·삭제를 제공해야 하며 1단계 대댓글까지 지원해야 한다.

- **REQ-BOARD-004 (첨부파일 업로드 — Event-driven)**
  사용자가 게시글에 파일을 첨부했을 때, 시스템은 파일 확장자 화이트리스트(jpg, png, pdf, hwp, docx, xlsx, zip 등)와 최대 크기(기본 10MB) 정책을 적용하고, 미통과 시 거부해야 한다. (egovframe cop/cmm/fms 차용)

- **REQ-BOARD-005 (첨부파일 보안 다운로드 — Event-driven)**
  사용자가 첨부파일 다운로드를 요청했을 때, 시스템은 권한을 재검증하고 Content-Disposition 헤더에 안전한 파일명을 설정하여 응답해야 하며, 다운로드 이력을 audit_log에 기록해야 한다.

- **REQ-BOARD-006 (공지사항 고정 — Optional)**
  공지사항 게시판에서 고정 옵션이 활성화된 경우, 시스템은 해당 공지를 목록 최상단에 고정 표시하고, 노출 기간(시작일·종료일)을 적용해야 한다. (egovframe cop/ntc 차용)

- **REQ-BOARD-007 (FAQ 카테고리 — Ubiquitous)**
  시스템은 FAQ를 카테고리 단위로 분류하여 등록·조회할 수 있도록 제공해야 한다. (egovframe cop/com/faq 차용)

- **REQ-BOARD-008 (Q&A 답변 워크플로우 — State-driven)**
  Q&A 게시글이 답변 대기 상태인 동안, 시스템은 운영자에게 답변 등록 권한을 부여하고, 답변이 등록되면 작성자에게 이메일 또는 시스템 알림을 발송해야 한다. (이메일 발송은 SMTP 설정 시 활성화)

- **REQ-BOARD-009 (게시글 첨부파일 다중 업로드 — Optional)**
  다중 업로드가 허용된 게시판인 경우, 시스템은 한 게시글에 최대 10개까지 파일을 첨부할 수 있어야 한다.

- **REQ-BOARD-010 (게시글 비공개 — Optional)**
  Q&A 등 비공개가 허용된 게시판인 경우, 시스템은 작성자와 운영자만 게시글을 조회할 수 있도록 제어해야 한다.

### 6.3 REQ-CONTENT-* : 콘텐츠·메뉴·사이트관리 (Bundle C)

- **REQ-CONTENT-001 (메뉴 트리 관리 — Ubiquitous)**
  시스템은 운영자가 트리 구조의 메뉴(부모-자식 관계, 정렬 순서, URL, 메뉴 유형)를 `/api/v1/content/menus` 로 관리할 수 있도록 제공해야 한다. (egovframe sym/mnu 차용)

- **REQ-CONTENT-002 (메뉴별 권한 매핑 — Ubiquitous)**
  시스템은 각 메뉴에 대해 접근 가능한 역할(Role) 집합을 매핑하고, 사용자의 역할에 따라 메뉴 노출을 제어해야 한다. (REQ-AUTH-008 의존)

- **REQ-CONTENT-003 (사이트 다중 정의 — Optional)**
  멀티사이트 옵션이 활성화된 경우, 시스템은 여러 사이트(예: 본사·지사) 각각에 독립적인 메뉴·템플릿·콘텐츠를 보유할 수 있어야 한다. (1차는 단일 사이트만 활성화)

- **REQ-CONTENT-004 (페이지 템플릿 관리 — Ubiquitous)**
  시스템은 운영자가 페이지 레이아웃 템플릿(헤더·푸터·사이드바·콘텐츠 영역)을 정의·재사용할 수 있도록 제공해야 한다. (egovframe uss/ion/tmm 차용)

- **REQ-CONTENT-005 (콘텐츠 위지윅 편집 — Ubiquitous)**
  시스템은 운영자가 위지윅 에디터(예: TinyMCE 또는 Toast UI Editor)로 콘텐츠를 작성·수정할 수 있도록 제공하며, 본문에 삽입 가능한 미디어는 사이트 자산(첨부파일)만 허용해야 한다.

- **REQ-CONTENT-006 (콘텐츠 버전 이력 — Ubiquitous)**
  시스템은 콘텐츠 수정 시마다 이전 버전을 content_history 테이블에 보존하고, 운영자가 특정 버전으로 롤백할 수 있도록 제공해야 한다.

- **REQ-CONTENT-007 (팝업 관리 — Ubiquitous)**
  시스템은 운영자가 팝업(제목·내용·노출 위치·시작일·종료일·우선순위)을 등록하고, 공공 사이트에서 기간·위치 조건에 맞는 팝업만 노출해야 한다.

- **REQ-CONTENT-008 (배너 관리 — Ubiquitous)**
  시스템은 배너(이미지·링크·노출 위치·정렬 순서)를 관리할 수 있어야 하며, 메인 페이지·서브 페이지별로 배너 슬롯을 구분해야 한다.

- **REQ-CONTENT-009 (페이지 발행 워크플로우 — State-driven)**
  콘텐츠가 "임시저장" 상태인 동안, 시스템은 작성자만 해당 콘텐츠를 미리보기 할 수 있어야 하며, "발행" 상태로 전환되면 공공 사이트에 즉시 노출해야 한다.

- **REQ-CONTENT-010 (다국어 콘텐츠 — Optional)**
  다국어 활성 콘텐츠인 경우, 시스템은 한국어·영어 버전을 별도 row로 관리하고, 사용자의 Accept-Language 또는 명시적 언어 선택에 따라 적절한 버전을 반환해야 한다.

### 6.4 REQ-SYSTEM-* : 통계·로그·시스템관리 (Bundle D)

- **REQ-SYSTEM-001 (접속로그 기록 — Ubiquitous)**
  시스템은 모든 페이지 요청에 대해 접속 일시·IP·User-Agent·요청 URL·세션 ID·응답 코드를 access_log 테이블에 기록해야 한다. (egovframe sym/log 차용)

- **REQ-SYSTEM-002 (방문자 통계 대시보드 — Ubiquitous)**
  시스템은 운영자가 일별·주별·월별 방문자 수, 페이지뷰, 인기 페이지 Top 10을 조회할 수 있도록 통계 대시보드를 제공해야 한다.

- **REQ-SYSTEM-003 (공통 코드 관리 — Ubiquitous)**
  시스템은 운영자가 공통 코드 그룹(code_group)과 상세 코드(code) 의 CRUD를 `/api/v1/system/codes` 로 수행할 수 있도록 제공해야 한다. (egovframe sym/ccm 차용)

- **REQ-SYSTEM-004 (시스템 운영 대시보드 — Ubiquitous)**
  시스템은 운영자에게 서버 헬스 상태(`/actuator/health`), 최근 24시간 audit_log 요약, 진행 중인 잠금 계정 수, 디스크/메모리 사용량을 단일 화면으로 제공해야 한다.

- **REQ-SYSTEM-005 (감사로그 조회 — Ubiquitous)**
  시스템은 운영자가 감사로그(audit_log)를 사용자·기간·도메인·액션 조건으로 검색할 수 있도록 제공해야 하며, 결과는 페이징하여 반환해야 한다.

- **REQ-SYSTEM-006 (감사로그 내보내기 — Optional)**
  운영자가 감사로그 내보내기를 요청한 경우, 시스템은 CSV 형식으로 결과를 제공해야 하며, 내보내기 행위 자체도 audit_log에 기록되어야 한다.

### 6.5 REQ-CROSS-* : 횡단 관심사

- **REQ-CROSS-001 (KWCAG 2.2 AA 준수 — Ubiquitous)**
  시스템의 모든 SPA 화면은 한국형 웹 콘텐츠 접근성 지침 2.2 레벨 AA를 준수해야 하며, axe-core 자동 검사에서 critical/serious 위반 0건을 유지해야 한다.

- **REQ-CROSS-002 (개인정보 암호화 — Ubiquitous)**
  시스템은 주민번호·휴대폰번호·이메일 등 개인식별정보를 AES-256(GCM)으로 암호화하여 저장해야 하며, 키는 환경변수 또는 시크릿 매니저에서 주입해야 한다.

- **REQ-CROSS-003 (개인정보 마스킹 — State-driven)**
  사용자가 일반 운영자 권한으로 개인정보를 조회하는 동안, 시스템은 휴대폰번호 가운데 4자리, 이메일 ID 부분 일부, 주민번호 뒷자리를 자동 마스킹하여 반환해야 한다.

- **REQ-CROSS-004 (감사로그 AOP — Ubiquitous)**
  시스템은 모든 도메인 Service 레이어의 C/U/D 메서드 진입·종료를 Spring AOP로 가로채어 audit_log 테이블에 사용자 ID, IP, 클래스명, 메서드명, 파라미터 요약, 결과 코드, 처리 시간을 기록해야 한다.

- **REQ-CROSS-005 (감사로그 변조 방지 — Ubiquitous)**
  시스템은 audit_log 테이블을 append-only로 운영해야 하며, UPDATE·DELETE를 데이터베이스 권한 수준에서 차단해야 한다.

- **REQ-CROSS-006 (다국어 메시지 리소스 — Ubiquitous)**
  시스템은 백엔드 메시지(예외·검증 메시지)를 `messages_ko.properties`, `messages_en.properties` 로 분리하고, 클라이언트의 Accept-Language 헤더에 따라 응답해야 한다.

- **REQ-CROSS-007 (다국어 콘텐츠 테이블 — Ubiquitous)**
  시스템은 다국어 콘텐츠를 `content_i18n` 테이블의 (content_id, lang) 복합키로 관리해야 한다.

- **REQ-CROSS-008 (Docker 컨테이너 배포 — Ubiquitous)**
  시스템은 Docker Multi-stage 빌드로 backend·admin-fe·public-fe 이미지를 생성하고, docker-compose.yml로 로컬 전체 스택을 기동할 수 있어야 한다.

- **REQ-CROSS-009 (관측성 — Ubiquitous)**
  시스템은 Spring Boot Actuator를 통해 `/actuator/health`, `/actuator/info`, `/actuator/prometheus` 엔드포인트를 노출해야 하며, 운영 환경에서는 내부망에서만 접근 가능하도록 nginx로 제한해야 한다.

- **REQ-CROSS-010 (구조화 로그 — Ubiquitous)**
  운영 환경에서 시스템은 Logback JSON 인코더로 구조화 로그를 stdout에 출력해야 하며, traceId 필드를 포함해 분산 추적과 연동 가능해야 한다.

---

## 7. 데이터 모델 개요

> 상세 DDL과 인덱스는 SPEC-CMS-002~005에서 정의. 본 SPEC은 핵심 테이블 목록만 제시.

### 7.1 Bundle A — 회원·권한·로그인

- `users` (id, login_id, password_hash, name, email_enc, phone_enc, status, created_at, updated_at)
- `roles` (id, role_code, role_name, description)
- `role_users` (user_id, role_id) — N:M 매핑
- `permissions` (id, permission_code, description)
- `role_permissions` (role_id, permission_id)
- `login_history` (id, user_id, ip, user_agent, result, reason, created_at)
- `password_history` (id, user_id, password_hash, created_at)
- `token_blacklist` (jti, expires_at)

### 7.2 Bundle B — 게시판·공지·Q&A·FAQ

- `bbs_master` (id, name, type, page_size, comment_yn, file_yn, role_required)
- `bbs_post` (id, master_id, title, content, author_id, view_count, fixed_yn, secret_yn, deleted_yn, created_at)
- `bbs_comment` (id, post_id, parent_id, content, author_id, deleted_yn, created_at)
- `bbs_attachment` (id, post_id, original_name, stored_name, mime_type, size_bytes, created_at)
- `faq` (id, category, question, answer, sort_order)
- `qna` (id, master_id, question, answer, author_id, answered_by, status, created_at)

### 7.3 Bundle C — 콘텐츠·메뉴·사이트관리

- `site` (id, code, name, default_lang)
- `menu` (id, site_id, parent_id, name, url, menu_type, sort_order)
- `menu_permission` (menu_id, role_id)
- `template` (id, name, html_layout, description)
- `page` (id, site_id, menu_id, template_id, slug, title, status)
- `content` (id, page_id, body, status, published_at)
- `content_history` (id, content_id, body_snapshot, modified_by, modified_at)
- `content_i18n` (content_id, lang, title, body)
- `popup` (id, title, content, position, start_at, end_at, priority)
- `banner` (id, image_url, link_url, position, sort_order, start_at, end_at)

### 7.4 Bundle D — 통계·로그·시스템관리

- `access_log` (id, user_id, ip, user_agent, request_url, response_code, session_id, created_at)
- `code_group` (group_code, group_name, description)
- `code` (group_code, code, code_name, sort_order, use_yn)
- `audit_log` (id, user_id, ip, class_name, method_name, params_summary, result_code, elapsed_ms, created_at) — append-only

---

## 8. API 설계 개요

> 모든 엔드포인트는 `/api/v1/{domain}/{resource}` 규약을 따른다. 페이징은 `?page=0&size=20&sort=createdAt,desc`. 에러 포맷은 `{ "code": "...", "message": "...", "traceId": "..." }`.

### 8.1 Bundle A 인증·권한

- `POST /api/v1/auth/login` — 로그인 (REQ-AUTH-001)
- `POST /api/v1/auth/refresh` — 토큰 갱신 (REQ-AUTH-002)
- `POST /api/v1/auth/logout` — 로그아웃 (REQ-AUTH-003)
- `GET|POST|PUT|DELETE /api/v1/auth/users` — 사용자 CRUD (REQ-AUTH-006)
- `GET|POST|PUT|DELETE /api/v1/auth/roles` — 역할 CRUD (REQ-AUTH-007)
- `POST /api/v1/auth/users/{id}/password` — 비밀번호 변경 (REQ-AUTH-009)
- `GET /api/v1/auth/login-history` — 로그인 이력 조회 (REQ-AUTH-011)

### 8.2 Bundle B 게시판

- `GET|POST|PUT|DELETE /api/v1/board/masters` — 게시판 마스터
- `GET|POST|PUT|DELETE /api/v1/board/posts` — 게시글 CRUD
- `GET|POST|DELETE /api/v1/board/posts/{id}/comments` — 댓글
- `POST /api/v1/board/posts/{id}/attachments` — 첨부 업로드
- `GET /api/v1/board/attachments/{id}/download` — 보안 다운로드
- `GET|POST|PUT|DELETE /api/v1/board/notices` — 공지사항
- `GET|POST|PUT|DELETE /api/v1/board/faqs` — FAQ
- `GET|POST|PUT|DELETE /api/v1/board/qnas` — Q&A

### 8.3 Bundle C 콘텐츠

- `GET|POST|PUT|DELETE /api/v1/content/menus` — 메뉴
- `POST /api/v1/content/menus/{id}/permissions` — 메뉴-역할 매핑
- `GET|POST|PUT|DELETE /api/v1/content/templates` — 템플릿
- `GET|POST|PUT|DELETE /api/v1/content/pages` — 페이지
- `GET|POST|PUT|DELETE /api/v1/content/contents` — 콘텐츠
- `GET /api/v1/content/contents/{id}/history` — 버전 이력
- `POST /api/v1/content/contents/{id}/rollback/{versionId}` — 롤백
- `GET|POST|PUT|DELETE /api/v1/content/popups` — 팝업
- `GET|POST|PUT|DELETE /api/v1/content/banners` — 배너

### 8.4 Bundle D 시스템

- `GET /api/v1/system/access-logs` — 접속로그 조회
- `GET /api/v1/system/stats/visitors` — 방문자 통계
- `GET|POST|PUT|DELETE /api/v1/system/codes` — 공통 코드
- `GET /api/v1/system/dashboard` — 운영 대시보드
- `GET /api/v1/system/audit-logs` — 감사로그 조회
- `GET /api/v1/system/audit-logs/export` — 감사로그 CSV 내보내기

---

## 9. 비기능 요구사항

| 카테고리 | 요구 |
|----------|------|
| 성능 (FE) | 메인 페이지 LCP < 2.5초, P95 응답 < 500ms |
| 성능 (BE) | 일반 조회 API P95 < 200ms, 통계 조회 P95 < 1초 |
| 보안 | OWASP Top 10 (A01~A10) 대응 — SQL Injection 방어(MyBatis #{}), XSS 방어(Thymeleaf 자동 이스케이프 또는 SPA 측 이스케이프), CSRF 보호(JWT + SameSite Cookie) |
| 가용성 | 단일 노드 99.5% (1차), K8s 전환 시 99.9% 목표 |
| 확장성 | DB 연결 풀 동시 20 처리, 향후 수평 확장 가능 구조 (stateless API) |
| 접근성 | KWCAG 2.2 AA, 키보드 네비게이션 100%, 색대비 4.5:1, 대체 텍스트 100% |
| 관측성 | 모든 요청 traceId 로깅, Prometheus 메트릭 노출, 헬스체크 엔드포인트 |
| 호환성 | 최신 Chrome/Edge/Firefox/Safari 2개 버전 지원, IE 미지원 |

---

## 10. 위험 및 가정

| ID | 위험·가정 | 영향 | 완화 방안 |
|----|----------|------|----------|
| RISK-01 | 공통컴포넌트 v5 DDL을 Oracle → PostgreSQL 변환 시 누락·오변환 가능성 | 데이터 무결성 손상 | Flyway V1 SQL 리뷰 + Testcontainers 통합 테스트 + research.md §2의 변환 매트릭스 준수 |
| RISK-02 | KWCAG 자동 검사 도구(axe-core)는 모든 위반을 잡지 못함 (수동 검수 필요 영역 존재) | 법적 미준수 위험 | 자동 검사 + 분기별 수동 감사 + 키보드 네비게이션 E2E 시나리오 추가 |
| RISK-03 | JWT Access Token 탈취 시 만료(15분) 전까지 악용 가능 | 권한 도용 | Refresh Token Rotation, IP 변경 시 강제 재인증, Refresh Token HttpOnly Cookie + SameSite=Strict |
| RISK-04 | 운영 환경에서 audit_log 테이블 폭증 시 성능 저하 | 응답 지연 | 월별 파티셔닝, 6개월 후 콜드 스토리지 이관 정책 (별도 SPEC) |
| RISK-05 | 위지윅 에디터 본문에 악성 스크립트 삽입 가능성 | XSS 공격 | DOMPurify로 서버 저장 전 sanitize + 출력 시 이스케이프 |
| ASSUM-01 | 1차 환경은 단일 서버 또는 소규모 클러스터 (K8s 전환은 후속) | — | docker-compose.prod.yml 우선 정비, K8s manifest는 후속 |
| ASSUM-02 | 외부 SMTP 서버는 1차에 미연동 (REQ-BOARD-008 알림은 인앱 알림으로 우선 구현) | 알림 범위 제한 | SMTP 설정 추가 시 활성화되도록 인터페이스 분리 |
| ASSUM-03 | TDD 방법론 채택, 테스트 커버리지 85% 강제 (quality.yaml 기준) | — | CI에서 JaCoCo·Vitest coverage 게이트 |

---

## 11. 마일스톤 / 후속 SPEC 분할

본 SPEC은 Umbrella이며, 우선순위에 따라 다음 하위 SPEC으로 분할하여 구현한다.

| 후속 SPEC ID | 범위 | 우선순위 | 비고 |
|-------------|------|----------|------|
| SPEC-CMS-002 | Bundle A — 회원·권한·로그인 상세 (REQ-AUTH-001~012) + DDL | P0 | 다른 묶음의 보안 기반, 가장 먼저 구현 |
| SPEC-CMS-003 | Bundle B — 게시판·공지·Q&A·FAQ 상세 (REQ-BOARD-001~010) + DDL | P0 | A에 의존 |
| SPEC-CMS-004 | Bundle C — 콘텐츠·메뉴·사이트관리 상세 (REQ-CONTENT-001~010) + DDL | P0 | A에 의존 |
| SPEC-CMS-005 | Bundle D — 통계·로그·시스템관리 상세 (REQ-SYSTEM-001~006) + DDL | P1 | A/B/C 운영 시점에 가치 발생 |
| SPEC-CMS-006 (예정) | KWCAG 2.2 AA 자동 검수 파이프라인 + 수동 감사 체크리스트 | P0 | REQ-CROSS-001 구현 |
| SPEC-CMS-007 (예정) | PIA 대응 — 암호화·마스킹·보존기간 정책 | P0 | REQ-CROSS-002~003 구현 |

마일스톤 진행 순서: SPEC-CMS-002 → (003, 004 병렬) → 005 → 006/007.

---

## 12. 참고 문서

- 전자정부 표준프레임워크 위키: https://www.egovframe.go.kr/wiki/
- 공통컴포넌트 v5 가이드: https://www.egovframe.go.kr/wiki/doku.php?id=egovframework:com:v5
- KWCAG 2.2: https://www.wa.or.kr/board/include/download.jsp?no=339&db=dat3&fno=1
- Spring Security 6 Reference: https://docs.spring.io/spring-security/reference/
- OpenAPI 3.1 Specification: https://spec.openapis.org/oas/v3.1.0
- OWASP Top 10 2021: https://owasp.org/Top10/

---

## 15. RFP 매핑 부록 (비즈패스파인더 고도화 용역, 2026-04-23)

### 15.1 출처

- 발주처: 중소벤처기업진흥공단 (KOSME) — 기업평가데이터실
- 사업명: 비즈패스파인더 고도화 용역
- 사업기간: 계약체결일로부터 180일 / 사업예산: 450,000천원 (부가세 포함)
- RFP 원문: `.moai/refs/RFP/비즈패스파인더 고도화 용역_제안요청서.pdf` (53 페이지)
- 요약 문서: `.moai/refs/rfp-summary.md`
- 갭 분석: `.moai/refs/rfp-gap-analysis.md` (v0.2 — 사용자 결정 반영)

본 부록은 RFP의 기능 요구사항을 iroum-cms에 통합하기 위한 매핑 표로, **기술 스택은 본 SPEC v0.1의 결정(Vue 3.5+ / Spring Boot 3.2.x / Java 17 / egovFrame v5.0.0 / PostgreSQL 16 / JWT)을 그대로 유지**하며, RFP의 MariaDB·Milvus·JSP·SSO 등은 옵션 SPEC으로만 처리한다.

### 15.2 SFR (기능 요구사항) → iroum-cms SPEC 매핑

| SFR ID | RFP 명칭 | iroum-cms 매핑 SPEC | 본 umbrella 추적 prefix | 우선 |
|---|---|---|---|---|
| SFR-001 | 실시간 데이터 동기화 | SPEC-CMS-009 (데이터 거버넌스) | REQ-DATA-* | P1 |
| SFR-002 | 성장단계 예측 모델 고도화 | SPEC-CMS-AI-001 (옵션) | REQ-AI-* | 옵션 |
| SFR-003 | 창업기업 가상 시뮬레이션 | SPEC-CMS-AI-001 또는 SPEC-CMS-008 | REQ-SIM-* | 옵션 |
| SFR-004 | 경영위험 예측 알고리즘 정교화 | SPEC-CMS-AI-001 (옵션) | REQ-AI-* | 옵션 |
| SFR-005 | 사고사례 매칭 알고리즘 | SPEC-CMS-006 (안전경영) | REQ-SAFETY-* | P0 |
| SFR-006 | 안전경영 가이드라인 자동 생성 | SPEC-CMS-006 (안전경영) | REQ-SAFETY-* | P0 |
| SFR-007 | 정책사업 지능형 매칭 | SPEC-CMS-007 (정책사업) | REQ-POLICY-* | P0 |
| SFR-008 | 적기 타겟팅 알림 (알림톡/메일) | SPEC-CMS-007 + SPEC-CMS-004 amendment | REQ-NOTI-* | P0 |
| SFR-009 | 시각화 대시보드 + UI/UX | SPEC-CMS-008 (대시보드) + SPEC-CMS-010 (검색) | REQ-VIZ-*, REQ-SEARCH-* | P0 |
| SFR-010 | 통합 홈페이지 이관 + SSO | SPEC-CMS-MIG-001 (옵션) + SPEC-CMS-002 amendment | REQ-MIG-*, REQ-AUTH-S-* | 옵션 |
| SFR-011 | 분석용 데이터 거버넌스 | SPEC-CMS-009 (데이터 거버넌스) | REQ-GOV-* | P1 |
| SFR-012 | 알고리즘 품질 모니터링 | SPEC-CMS-AI-001 (옵션) | REQ-MON-* | 옵션 |
| SFR-013 | 통합 KPI 대시보드 | SPEC-CMS-008 + SPEC-CMS-005 amendment | REQ-KPI-* | P0 |
| SFR-014 | 통합 관리자 권한 + 메뉴 (4단계 RBAC) | SPEC-CMS-002 amendment | REQ-AUTH-013-D~ | P0 |
| SFR-015 | 시스템 로그 + 연계 이력 | SPEC-CMS-005 amendment | REQ-SYSTEM-008-D~ | P0 |

총 15개 SFR 모두 신규 SPEC 5개(006/007/008/009/010) + 기존 SPEC-CMS-002~005 amendment + 옵션 트랙 2개로 완전 커버.

### 15.3 PER (성능 요구사항) — 모든 SPEC에 비기능으로 반영

- **PER-002**: CPU/Memory/Disk 평균 90% 미만
- **PER-003**: 검색 응답 3초 이내, 일배치 10분 이내, 월배치 1시간 이내
- **PER-004**: 초당 50건 처리, 동시 사용자 1,000명, 임계 90% 시 지연 안내 페이지 노출

본 SPEC §9 비기능 요구사항(LCP < 2.5초, 일반 조회 P95 < 200ms)과 충돌하지 않으며, RFP 임계값을 상한으로 추가 반영한다.

### 15.4 INR (인터페이스) — SPEC-CMS-008 시각화 / SPEC-CMS-010 검색에 통합

- **INR-004**: 메인 시안 3종 제시 (SPEC-CMS-008 acceptance 기준)
- **INR-009**: Cross Browser (본 SPEC §9 호환성과 일치)
- **INR-011**: 표준 연계모듈 + 공공 OpenAPI (SPEC-CMS-007 정책사업·SPEC-CMS-009 데이터 수집)

### 15.5 DAR (데이터) — SPEC-CMS-009 데이터 거버넌스에 통합

- **DAR-001**: 데이터 표준 사전 (S-Meta / DA# 모델등록시스템 호환 구조 — RFP 발주기관 시스템)
- **DAR-007**: 메타데이터 등록·현행화 (발주기관 메타시스템 + 중앙메타시스템)
- **DAR-009**: 보안사고 시 복구 시간 내 복원 (RTO/RPO 정의)

### 15.6 SER (보안) — SPEC-CMS-002 / SPEC-CMS-003 보강

- **SER-002**: 가명/합성정보 처리, 고유식별번호(주민번호·계좌·휴대폰) AES-256-GCM 암호화 (REQ-CROSS-002 강화)
- **SER-004**: SQL Injection / XSS / 파일 다운로드 / URL 임의 변경 방지 (REQ-AUTH-008·REQ-BOARD-005 강화)

### 15.7 COR (제약사항) — 모든 SPEC 기본 준수

- **COR-001**: KWCAG 2.2 (이미 REQ-CROSS-001로 반영)
- **COR-005**: 행안부 시큐어 코딩 가이드 준수 (이미 §9 보안에 반영)

### 15.8 채택 제외 RFP 항목 (기술 스택 차이)

| RFP 명세 | iroum-cms 결정 | 사유 |
|---|---|---|
| MariaDB + Milvus | PostgreSQL 16 (CMS) + 옵션 Milvus (AI 트랙) | tech.md FROZEN, JSONB·CTE·GIN 인덱스로 CMS 충분 |
| JSP 2.1 | Vue 3.5 SPA (관리자/사용자 모두) | KWCAG 2.2 / 반응형 / 운영자 UX |
| 상급기관 SSO API | JWT 기본 + 옵션 SPEC-CMS-MIG-001 | 자체 프로젝트로 외부 IdP 의존 회피 |
| Apache + 외부 Tomcat | Spring Boot 내장 Tomcat 10 + Nginx | 컨테이너 친화 단순 스택 |
| 통합파일서버 (SER-006) | Local FS / 객체 스토리지 (선택) | 단일 기관 단일 사이트 범위 |
| KWCAG 2.1 (RFP 기술표) | KWCAG 2.2 AA (본 SPEC 유지) | RFP 본문 COR-001 자체가 2.2이므로 일치 |

---

## 16. 확장 SPEC 트리 (v0.2 RFP 통합 후)

### 16.1 트리 구조

```
SPEC-CMS-001 [umbrella v0.2]
├─ 기존 (1차 출시 핵심) — Bundle A/B/C/D 상세 SPEC
│  ├─ SPEC-CMS-002 회원·권한·로그인 [+ amendment v0.2: 4단계 RBAC, 부서 권한, SSO 옵션 인터페이스]
│  ├─ SPEC-CMS-003 게시판·공지·Q&A·FAQ [+ amendment v0.2: 다중 게시판 유형, 설문조사 연계]
│  ├─ SPEC-CMS-004 콘텐츠·메뉴·사이트관리 [+ amendment v0.2: 알림 템플릿, 메타데이터 항목 확장]
│  └─ SPEC-CMS-005 통계·로그·시스템관리 [+ amendment v0.2: KPI 대시보드, SSO 로그, 성능 임계값]
├─ RFP 신규 P0 (사용자 가시성 큰 도메인)
│  ├─ SPEC-CMS-006 안전경영 + 사고사례 매칭 [SFR-005, SFR-006]
│  ├─ SPEC-CMS-007 정책사업 매칭 + 적기 알림 [SFR-007, SFR-008]
│  └─ SPEC-CMS-008 시각화 대시보드 + KPI [SFR-009, SFR-013]
├─ RFP 신규 P1 (운영·확장)
│  ├─ SPEC-CMS-009 데이터 거버넌스 [SFR-001, SFR-011, DAR 전체]
│  └─ SPEC-CMS-010 통합 검색 엔진 [INR + SFR-009 자동완성·인기검색어]
├─ 홍익인간 CMS gap 신규 P0
│  └─ SPEC-CMS-MEDIA-001 통합 미디어 라이브러리 [홍익 #5]
└─ 옵션 트랙 (별도 일정/리소스 결정 필요)
   ├─ SPEC-CMS-AI-001 AI/ML + Milvus + 알고리즘 모니터링 [SFR-002, 003, 004, 012]
   └─ SPEC-CMS-MIG-001 통합 홈페이지 이관 + SSO [SFR-010] — DEPRECATED (자체 프로젝트로 확정, 비즈패스파인더 응찰 시나리오 외에는 불필요)
```

### 16.2 amendment 영향 추정 (LOC)

| 구분 | 대상 | 추정 LOC 증가 |
|---|---|---:|
| 기존 보강 | SPEC-CMS-002~005 amendment v0.2 | +1,500 |
| 신규 P0 | SPEC-CMS-006 / 007 / 008 신규 | +5,000 |
| 신규 P1 | SPEC-CMS-009 / 010 신규 | +3,000 |
| 옵션 트랙 | SPEC-CMS-AI-001 (AI/ML, 잠재 P1) / ~~SPEC-CMS-MIG-001~~ (DEPRECATED) | +2,000 |
| 홍익 P0 | SPEC-CMS-MEDIA-001 신규 + SPEC-CMS-002 v0.3 amendment | +2,000 |
| **누적** | 현재 8,839 + 추가 12,000~14,000 + 홍익 +2,000 | **약 23,000~25,000** |

### 16.3 작성 순서 권장

1. **SPEC-CMS-001 v0.2 amendment** (본 작업) — 본 SPEC §15~17 추가, §14 갱신
2. **SPEC-CMS-002~005 amendment v0.2 일괄** — 기존 SPEC에 RFP 비기능·SFR 매핑 추가
3. **신규 SPEC P0 (006/007/008)** — 병렬 가능. 안전경영·정책사업·대시보드 동시 진행
4. **신규 SPEC P1 (009/010)** — P0 SPEC 완료 후 데이터 거버넌스·검색 엔진
5. **옵션 트랙 (SPEC-CMS-AI-001, SPEC-CMS-MIG-001)** — 별도 사용자 승인 시점에 착수

### 16.4 의존 관계

- SPEC-CMS-006/007/008/009/010 모두 **SPEC-CMS-002 (인증·권한)** 에 의존
- SPEC-CMS-007 (정책 알림) 은 **SPEC-CMS-004 (콘텐츠·알림 템플릿) amendment** 에 의존
- SPEC-CMS-008 (KPI 대시보드) 은 **SPEC-CMS-005 (통계 집계) amendment** 에 의존
- SPEC-CMS-009 (데이터 거버넌스) 는 **SPEC-CMS-005 (시스템·배치)** 에 의존
- SPEC-CMS-AI-001 (옵션) 은 **SPEC-CMS-009 (데이터 파이프라인)** 에 의존

---

## 17. RFP 비기능 횡단 요구사항 적용 정책

본 amendment에서 RFP 비기능 요구사항(PER, SER, DAR 일부)을 모든 child SPEC에 일괄 적용한다. 각 child SPEC의 `## 비기능 요구사항` 섹션과 acceptance.md `5 Quality Gates` 섹션에 다음 항목을 명시한다.

### 17.1 성능 임계값 (PER-002~004)

모든 child SPEC의 비기능 요구사항 섹션에 다음을 명시한다.

- CPU / Memory / Disk 평균 사용률 90% 미만 유지 (PER-002)
- 검색·조회 API p95 < 3초 (PER-003) — 본 SPEC §9의 일반 조회 P95 200ms는 정상 부하, 3초는 RFP 상한값
- 일별 배치 < 10분 / 월별 배치 < 1시간 (PER-003)
- 동시 처리 초당 50건, 동시 사용자 1,000명, 임계 90% 도달 시 지연 안내 페이지 노출 (PER-004)

### 17.2 보안 강화 (SER-002~004)

- **SPEC-CMS-002 (회원·권한)**: 고유식별번호 (주민·계좌·휴대폰) AES-256-GCM 암호화 의무 (REQ-CROSS-002 강화)
- **SPEC-CMS-003 (게시판)**: SQL Injection / XSS / 파일다운로드 / URL 임의변경 방지 (이미 반영, RFP 명시로 강화)
- **모든 SPEC**: 가명·합성정보 처리 옵션, 행안부 시큐어 코딩 가이드 준수, 패스워드 하드코딩 절대 금지

### 17.3 데이터 거버넌스 (DAR-001~010)

모든 child SPEC의 데이터 모델 섹션에 다음을 명시한다.

- 표준 명명 규칙: Java camelCase, DB snake_case
- 데이터 분류체계: 마스터 / 거래 / 통계 / 로그
- 메타데이터 항목 (S-Meta / DA# 호환 구조):
  - 테이블·컬럼 한글명
  - 데이터 표준 도메인
  - 변경 이력 (DAR-002)
- DAR-009 복구 시간: RTO ≤ 4시간 (목표값, 인프라 SPEC에서 사용자 협의 필요)

### 17.4 품질 게이트 추가 (QUR-004)

모든 child SPEC의 `acceptance.md` 5 Quality Gates 섹션에 다음을 추가한다.

- **QG-COMMON-1**: 결함 발생률 시험 운영 기간 동안 5% 미만 (QUR-004)
- **QG-COMMON-2**: P0 결함 지속시간 1시간 이내 (QUR-004)

### 17.5 기술적용계획표 (COR-001 별첨)

본 SPEC은 **비즈패스파인더와 무관한 자체 공공기관 CMS 프로젝트**이며, RFP는 기능 참고 자료로만 활용한다 (사용자 결정 2026-04-29). 본 절은 미래에 유사 RFP 응찰 또는 공공기관 도입 시 활용할 수 있는 기술 상향 사유 참고표이며, 본 프로젝트의 직접 산출물 범위는 아니다.

| RFP 기술표 항목 | 본 프로젝트 적용 | 상향 사유 |
|---|---|---|
| JSP 2.1 | Vue 3.5 SPA | SPA 기반 모던 UX, 메인 시안 3종(INR-004) 충족, KWCAG 2.2 AA 자동 검사 가능 |
| egovFrame v3 또는 v4 | egovFrame v5.0.0 | 최신 LTS, Spring Boot 3.2 / JDK 17 호환, 보안 패치 |
| KWCAG 2.1 | KWCAG 2.2 AA | RFP 본문 COR-001 자체가 2.2이며 더 강한 표준 |

---

## 18. 홍익인간 CMS Gap 분석 부록 (참고 자료, 2026-04-29)

### 18.1 출처

- 참고 사이트: https://www.yooncoms.com/cms (홍익인간 CMS 제품 페이지)
- 분석일: 2026-04-29
- 사용자 결정: P0 핵심 3개만 채택 (본인인증, 회원정보 접근로그, 통합 미디어 라이브러리)
- 미채택 항목: GA4 연동, OAuth SNS, 페이지 롤백 워크플로, 더블린 코어, 일정/예약, 멀티사이트 활성화, 플러그인 아키텍처, 스킨 시스템 (총 8개 — 향후 v0.4+ 검토)

### 18.2 채택 P0 3개

| 갭 항목 | 적용 SPEC | 신규 REQ ID |
|---|---|---|
| 본인인증 (휴대폰 OTP + 이메일) | SPEC-CMS-002 v0.3 | REQ-AUTH-017-D |
| 회원정보 접근 로그 | SPEC-CMS-002 v0.3 | REQ-AUTH-018-D |
| 통합 미디어 라이브러리 | **SPEC-CMS-MEDIA-001 신규** | REQ-MEDIA-001~005-D |

### 18.3 미채택 항목 사유

| 미채택 | 사유 | 미래 검토 |
|---|---|---|
| Google Analytics 4 사이트별 연동 | 자체 KPI(SPEC-005)로 충분, GA 의존 회피 | v0.4+ |
| OAuth SNS (네이버/카카오/구글) | SSO Provider 인터페이스(SPEC-002 v0.2) 활용 가능, 어댑터는 후속 | v0.4+ |
| 페이지 버전 롤백 워크플로 | page_history 모델 있음, 롤백 UI 후속 | v0.5+ |
| 더블린 코어 메타데이터 | S-Meta/DA# 우선, dc:* 컬럼 추가는 후속 | v0.5+ |
| 일정·예약 프로그램 | 별도 도메인, 본 1차 범위 외 | 신규 SPEC-CMS-CAL-001 가능 |
| 멀티사이트 활성화 | site 테이블 1차는 단일, 활성화는 2차 | v1.0+ |
| 플러그인 아키텍처 | 모놀리식 1차, 플러그인은 v2.0급 | 장기 |
| 스킨 패키지 시스템 | template 모델 있음, 패키지 핫스왑은 후속 | v0.5+ |

### 18.4 iroum-cms 우위 영역 (홍익인간 CMS 대비)

- KWCAG 2.2 AA 명시 + 자동 검증 (홍익은 명시 없음)
- JWT Refresh Rotation + 탈취 감지 (SPEC-002)
- 4단계 RBAC + 부서 (SPEC-002 v0.2)
- 감사로그 APPEND-ONLY + DB 트리거 (SPEC-005)
- Vue 3.5 SPA + TypeScript (홍익은 JSP+Spring MVC)
- 카카오 알림톡 + Q&A 자동화 (SPEC-003 v0.2)
- 외부 연계 로그 분리 + 6개월 보존 (SPEC-005 v0.2)

---

## 19. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.1 | 2026-04-29 | manager-spec | 초안 작성 (Umbrella SPEC) |
| v0.2 | 2026-04-29 | MoAI orchestrator | RFP 통합 amendment (§15~17 신설, RFP 69개 요구사항 매핑, 확장 SPEC 트리 SPEC-CMS-006~010 + 옵션 트랙 정의, 비기능 횡단 적용 정책 수립) |
| v0.3 | 2026-04-29 | MoAI orchestrator | 홍익인간 CMS gap 분석 통합 (§18 부록 신설), SPEC-CMS-MEDIA-001 트리 추가, SPEC-CMS-002 v0.3 reference (REQ-AUTH-017/018-D) |
