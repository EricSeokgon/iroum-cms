# SPEC-CMS-001: 공공기관 CMS — 1차 출시 기반(Umbrella SPEC)

## 1. 개요

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-001 |
| 제목 | Public-Institution CMS — First Release Foundation |
| 작성일 | 2026-04-29 |
| 작성자 | manager-spec (MoAI) |
| 상태 | Draft |
| 우선순위 | P0 |
| 분류 | Umbrella SPEC (후속 SPEC-CMS-002 ~ SPEC-CMS-005 분할 예정) |
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

## 13. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.1 | 2026-04-29 | manager-spec | 초안 작성 (Umbrella SPEC) |
