# SPEC-CMS-001 Acceptance Criteria

> 본 문서는 spec.md의 모든 REQ-* 요구사항에 대응하는 Given/When/Then 형식의 인수 조건을 정의한다.
> 각 인수 조건은 자동화 테스트(JUnit, Vitest, Playwright + axe-core) 또는 운영자 수동 검수로 검증된다.

---

## A. 회원·권한·로그인 (Bundle A)

### REQ-AUTH-001 — 사용자 로그인

**Given** 등록된 사용자(`login_id=admin`, BCrypt 해시된 비밀번호)와 활성 상태 계정이 존재하고
**When** 클라이언트가 `POST /api/v1/auth/login` 으로 올바른 자격증명 JSON `{loginId, password}` 을 전송하면
**Then** 200 OK + 응답 본문에 `accessToken`(JWT, exp=15분) 가 포함되고
**And** 응답 헤더 `Set-Cookie` 에 `refreshToken=...; HttpOnly; Secure; SameSite=Strict; Max-Age=604800` 이 포함되며
**And** `login_history` 테이블에 `result=SUCCESS` 행이 추가된다.

### REQ-AUTH-001 (실패 케이스)

**Given** 등록된 사용자가 존재하고
**When** 클라이언트가 잘못된 비밀번호로 `POST /api/v1/auth/login` 을 호출하면
**Then** 401 Unauthorized + 에러 코드 `AUTH_INVALID_CREDENTIALS` 가 반환되고
**And** `login_history` 테이블에 `result=FAILURE, reason=INVALID_PASSWORD` 행이 추가된다.

### REQ-AUTH-002 — Access Token 갱신

**Given** 클라이언트가 유효한 Refresh Token(HttpOnly Cookie)을 보유하고
**When** `POST /api/v1/auth/refresh` 를 호출하면
**Then** 200 OK + 새 Access Token이 응답 본문에 포함되고
**And** 새 Refresh Token이 새 Set-Cookie 헤더로 전달되며 (Rotation)
**And** 이전 Refresh Token은 `token_blacklist` 에 등록된다.

### REQ-AUTH-003 — 로그아웃

**Given** 인증된 사용자가 활성 Refresh Token을 보유하고
**When** `POST /api/v1/auth/logout` 을 호출하면
**Then** 204 No Content가 반환되고
**And** Refresh Token이 `token_blacklist` 에 추가되며
**And** 응답에 Refresh Cookie를 만료시키는 Set-Cookie 헤더가 포함된다.

### REQ-AUTH-004 — 비밀번호 정책

**Given** 새 사용자 등록 요청이 들어왔고
**When** 비밀번호가 `abc123` (8자 미만, 특수문자 없음) 이면
**Then** 400 Bad Request + 에러 코드 `AUTH_PASSWORD_POLICY_VIOLATION` 이 반환된다.

**And When** 비밀번호가 `Abcdef!1` (8자, 3종 조합) 이면
**Then** 등록이 성공하고 DB에 BCrypt 해시(strength=12) 가 저장된다.

### REQ-AUTH-005 — 계정 잠금

**Given** 한 사용자가 5회 연속 로그인 실패한 직후
**When** 6회째 로그인 시도가 들어오면
**Then** 423 Locked + 에러 코드 `AUTH_ACCOUNT_LOCKED` 가 반환되고
**And** 계정의 `locked_until` 필드가 현재 + 30분으로 설정된다.

### REQ-AUTH-006 — 사용자 CRUD

**Given** 운영자 권한을 가진 사용자가 인증된 상태에서
**When** `POST /api/v1/auth/users` 로 새 사용자 정보를 전송하면
**Then** 201 Created + 생성된 사용자 ID가 반환되고
**And** `users` 테이블에 BCrypt 해시된 비밀번호와 함께 행이 추가된다.

### REQ-AUTH-007 — 역할 관리

**Given** 운영자가 인증된 상태에서
**When** `POST /api/v1/auth/roles` 로 새 역할(role_code=`EDITOR`)을 생성하고 `POST /api/v1/auth/users/{id}/roles` 로 사용자에게 매핑하면
**Then** 매핑된 사용자가 다음 요청부터 EDITOR 역할의 권한을 적용받는다 (JWT claims 또는 DB 조회).

### REQ-AUTH-008 — 메뉴별 권한 검사

**Given** 사용자가 `VIEWER` 역할만 보유하고 메뉴 `MENU_USER_MGMT` 의 권한은 `ADMIN` 역할만 매핑된 상태에서
**When** 사용자가 `GET /api/v1/auth/users` 를 호출하면
**Then** 403 Forbidden + 에러 코드 `AUTH_PERMISSION_DENIED` 가 반환된다.

### REQ-AUTH-009 — 비밀번호 변경

**Given** 인증된 사용자가
**When** `POST /api/v1/auth/users/{id}/password` 로 현재 비밀번호와 새 비밀번호를 전송하면
**Then** 200 OK가 반환되고
**And** `password_history` 테이블에 이전 비밀번호 해시가 보존된다.

### REQ-AUTH-010 — 비밀번호 재사용 금지

**Given** 사용자가 직전 3회 사용한 비밀번호 중 하나를
**When** 비밀번호 변경 요청에 사용하면
**Then** 400 Bad Request + 에러 코드 `AUTH_PASSWORD_REUSED` 가 반환된다.

### REQ-AUTH-011 — 로그인 이력

**Given** 임의의 로그인 시도(성공·실패) 가 발생한 후
**When** 운영자가 `GET /api/v1/auth/login-history?userId={id}` 를 조회하면
**Then** 시도 기록(시각, IP, User-Agent, 결과) 이 시간 역순으로 페이징되어 반환된다.

### REQ-AUTH-012 — 강제 로그아웃

**Given** 관리자 강제 로그아웃 기능이 활성화된 상태에서
**When** 관리자가 `POST /api/v1/auth/users/{id}/force-logout` 을 호출하면
**Then** 해당 사용자의 모든 활성 Refresh Token이 `token_blacklist` 에 추가되어 즉시 무효화된다.

---

## B. 게시판·공지·Q&A·FAQ (Bundle B)

### REQ-BOARD-001 — 게시판 마스터 정의

**Given** 운영자가 인증된 상태에서
**When** `POST /api/v1/board/masters` 로 게시판 메타(이름, 유형=일반, 페이징=20, 댓글=Y, 첨부=Y, 권한=USER) 를 전송하면
**Then** 201 Created + 마스터 ID가 반환되고 `bbs_master` 테이블에 행이 추가된다.

### REQ-BOARD-002 — 게시글 CRUD + 페이징·검색

**Given** 게시판 마스터에 30건의 게시글이 존재하고
**When** `GET /api/v1/board/posts?masterId={id}&page=0&size=10&sort=createdAt,desc&keyword=공지` 를 호출하면
**Then** 200 OK + 검색어 매치 게시글이 최대 10건, 정렬 기준대로 반환된다.

### REQ-BOARD-003 — 댓글

**Given** 댓글 허용된 게시판의 게시글이 존재하고
**When** 인증 사용자가 `POST /api/v1/board/posts/{id}/comments` 로 댓글을 등록하면
**Then** 201 Created가 반환되고 `bbs_comment` 테이블에 `parent_id=NULL` 행이 추가된다.

**And When** 같은 사용자가 그 댓글의 ID를 `parentId` 로 지정하여 재요청하면
**Then** 1단계 대댓글이 생성된다 (parent_id != NULL).

### REQ-BOARD-004 — 첨부파일 업로드 검증

**Given** 게시판이 첨부 허용 상태이고
**When** 사용자가 `.exe` 확장자 파일을 업로드 시도하면
**Then** 400 Bad Request + 에러 코드 `FILE_EXTENSION_NOT_ALLOWED` 가 반환된다.

**And When** 11MB 크기의 PDF를 업로드하면 (기본 한도 10MB)
**Then** 413 Payload Too Large + 에러 코드 `FILE_SIZE_EXCEEDED` 가 반환된다.

### REQ-BOARD-005 — 보안 다운로드

**Given** 첨부파일이 존재하고 사용자가 해당 게시글 조회 권한을 보유하면
**When** `GET /api/v1/board/attachments/{id}/download` 를 호출하면
**Then** 200 OK + 적절한 Content-Disposition 헤더(파일명 RFC 5987 인코딩) 와 함께 파일이 반환되고
**And** `audit_log` 에 다운로드 이벤트(class=AttachmentService, method=download) 가 기록된다.

### REQ-BOARD-006 — 공지사항 고정

**Given** 운영자가 공지 게시글을 `fixedYn=Y`, `fixedFrom=오늘`, `fixedUntil=오늘+7일` 로 등록하면
**When** 일반 사용자가 공지사항 목록을 조회하면
**Then** 해당 공지가 다른 게시글보다 먼저(목록 최상단) 반환되고
**And** 8일 후 동일 조회 시에는 일반 게시글과 동일 정렬 위치로 노출된다.

### REQ-BOARD-007 — FAQ 카테고리

**Given** 운영자가 FAQ를 `category=ACCOUNT, ACCESS, CONTENT` 로 분류하여 5건씩 등록하면
**When** `GET /api/v1/board/faqs?category=ACCOUNT` 를 호출하면
**Then** 해당 카테고리의 FAQ 5건만 반환된다.

### REQ-BOARD-008 — Q&A 답변 워크플로우

**Given** 사용자가 Q&A 게시글을 등록하면 `status=PENDING` 상태가 되고
**When** 운영자가 `POST /api/v1/board/qnas/{id}/answer` 로 답변을 작성하면
**Then** `status=ANSWERED` 로 전환되고 인앱 알림이 작성자에게 등록된다 (SMTP 미연동 시 이메일 발송은 생략).

### REQ-BOARD-009 — 다중 첨부

**Given** 다중 업로드가 허용된 게시판이고
**When** 사용자가 동일 게시글에 11개 파일 첨부 시도를 하면
**Then** 400 Bad Request + 에러 코드 `FILE_COUNT_EXCEEDED` 가 반환된다 (한도 10).

### REQ-BOARD-010 — 비공개 게시글

**Given** 비공개 허용 게시판에서 작성자가 `secretYn=Y` 로 게시글을 등록하면
**When** 다른 일반 사용자가 해당 게시글 상세 조회를 요청하면
**Then** 403 Forbidden 또는 게시글 목록에서 본문 미노출 처리되고
**And** 작성자 본인 또는 운영자만 정상 조회한다.

---

## C. 콘텐츠·메뉴·사이트관리 (Bundle C)

### REQ-CONTENT-001 — 메뉴 트리

**Given** 운영자가 인증된 상태에서
**When** `POST /api/v1/content/menus` 로 부모 메뉴 생성 후 자식 메뉴를 `parentId` 지정해 생성하면
**Then** `GET /api/v1/content/menus?siteId={id}` 응답에 트리 구조 (parent → children) 가 정렬 순서대로 포함된다.

### REQ-CONTENT-002 — 메뉴 권한 매핑

**Given** 메뉴 `MENU_BOARD_ADMIN` 에 역할 `ADMIN` 만 매핑된 상태에서
**When** `VIEWER` 역할 사용자가 관리자 SPA에서 메뉴 목록을 조회하면
**Then** 응답 메뉴 목록에 `MENU_BOARD_ADMIN` 이 포함되지 않는다 (또는 disabled 상태로 노출).

### REQ-CONTENT-003 — 멀티사이트 (Optional)

**Given** 멀티사이트 옵션이 비활성(1차 기본값) 상태에서
**When** API가 site_id 없이 호출되면
**Then** 시스템 기본 사이트(default site) 의 컨텍스트로 응답한다.

### REQ-CONTENT-004 — 페이지 템플릿

**Given** 운영자가 `template_main`, `template_sub` 두 템플릿을 등록하고
**When** 페이지 생성 시 `templateId=template_sub` 를 지정하면
**Then** 해당 페이지가 sub 레이아웃으로 렌더링된다.

### REQ-CONTENT-005 — 위지윅 편집

**Given** 운영자가 위지윅 에디터로 콘텐츠를 작성하고
**When** 본문에 외부 도메인 이미지 URL을 삽입하려 하면
**Then** 클라이언트 측에서 차단 메시지가 노출되거나, 서버 저장 시 sanitize 처리로 외부 URL이 제거된다.

### REQ-CONTENT-006 — 버전 이력 + 롤백

**Given** 콘텐츠가 v1, v2, v3 까지 수정된 상태에서
**When** 운영자가 `POST /api/v1/content/contents/{id}/rollback/{versionId=v1}` 를 호출하면
**Then** 현재 콘텐츠 본문이 v1 의 본문으로 복원되고, `content_history` 에 롤백 이력(v4) 이 추가된다.

### REQ-CONTENT-007 — 팝업 노출 제어

**Given** 팝업이 `start_at=어제`, `end_at=내일`, `position=MAIN_TOP` 으로 등록되면
**When** 시민이 메인 페이지를 방문하면
**Then** 해당 팝업이 노출된다.

**And When** `end_at` 이후 방문하면
**Then** 팝업이 노출되지 않는다.

### REQ-CONTENT-008 — 배너 슬롯

**Given** 운영자가 `position=MAIN_HERO` 슬롯에 배너 3개를 등록하면
**When** 시민이 메인 페이지를 조회하면
**Then** 해당 슬롯에 정렬 순서대로 배너 3개가 렌더링된다.

### REQ-CONTENT-009 — 발행 워크플로우

**Given** 콘텐츠가 `status=DRAFT` 상태에서
**When** 시민이 공공 사이트에서 해당 콘텐츠 URL에 접근하면
**Then** 404 Not Found가 반환된다.

**And When** 운영자가 `status=PUBLISHED` 로 전환하면
**Then** 즉시 시민 접근 시 200 OK + 콘텐츠 본문이 반환된다.

### REQ-CONTENT-010 — 다국어 콘텐츠

**Given** `content_i18n` 테이블에 (content_id=1, lang=ko) 와 (content_id=1, lang=en) 두 행이 존재하고
**When** 시민이 `Accept-Language: en` 헤더로 콘텐츠를 조회하면
**Then** 영어 본문이 반환된다.

**And When** 헤더가 `ko` 또는 미지정이면
**Then** 한국어 본문이 반환된다.

---

## D. 통계·로그·시스템관리 (Bundle D)

### REQ-SYSTEM-001 — 접속로그 기록

**Given** 시민이 임의의 페이지에 HTTP 요청을 보내면
**When** 응답이 처리되면
**Then** `access_log` 테이블에 (created_at, ip, user_agent, request_url, response_code, session_id) 행이 추가된다.

### REQ-SYSTEM-002 — 방문자 통계

**Given** 지난 30일간 다양한 IP에서 1000건의 access_log가 누적되어 있고
**When** 운영자가 `GET /api/v1/system/stats/visitors?period=DAILY&from=...&to=...` 를 호출하면
**Then** 일자별 unique IP 카운트와 페이지뷰가 반환된다.

### REQ-SYSTEM-003 — 공통 코드 관리

**Given** 운영자가 `POST /api/v1/system/codes/groups` 로 코드 그룹 `GENDER` 를 생성한 후
**When** `POST /api/v1/system/codes` 로 코드 `M=남성, F=여성` 두 행을 등록하면
**Then** `code_group` 과 `code` 테이블에 정상 적재되고, `GET /api/v1/system/codes?groupCode=GENDER` 응답에 두 코드가 반환된다.

### REQ-SYSTEM-004 — 운영 대시보드

**Given** 시스템이 정상 가동 중이고
**When** 운영자가 `GET /api/v1/system/dashboard` 를 호출하면
**Then** `health=UP`, 최근 24h audit_log 카운트, 잠금 계정 수, 디스크/메모리 사용량을 포함한 단일 응답이 반환된다.

### REQ-SYSTEM-005 — 감사로그 검색

**Given** `audit_log` 에 다양한 사용자·도메인의 행이 누적된 상태에서
**When** 운영자가 `GET /api/v1/system/audit-logs?userId=...&from=...&to=...&domain=board` 를 호출하면
**Then** 조건에 일치하는 로그가 페이징되어 반환된다.

### REQ-SYSTEM-006 — 감사로그 CSV 내보내기

**Given** 운영자가 검색 조건에 매칭되는 100건의 audit_log를 보유한 상태에서
**When** `GET /api/v1/system/audit-logs/export?...` 를 호출하면
**Then** 200 OK + Content-Type=text/csv + Content-Disposition=attachment 응답이 반환되고
**And** 내보내기 행위 자체도 audit_log에 기록된다.

---

## E. 횡단 관심사 (Cross-Cutting)

### REQ-CROSS-001 — KWCAG 2.2 AA

**Given** Playwright E2E 테스트 환경에서
**When** 모든 SPA 화면(60~80 pages) 에 axe-core를 실행하면
**Then** critical · serious 위반이 0건이다.

**And** 키보드만 사용한 주요 워크플로우(로그인 → 게시글 작성 → 발행) 가 정상 완료된다.

### REQ-CROSS-002 — 개인정보 암호화

**Given** 사용자 등록 시 휴대폰번호 `010-1234-5678`이 입력되면
**When** DB에 저장된 후 `users.phone_enc` 컬럼을 직접 조회하면
**Then** 평문이 아닌 AES-256(GCM) 암호문이 저장되어 있다 (Base64 인코딩).

### REQ-CROSS-003 — 마스킹

**Given** 일반 운영자가 사용자 목록을 조회하고
**When** API 응답을 검사하면
**Then** 휴대폰번호는 `010-****-5678`, 이메일은 `ad***@example.com` 형태로 마스킹되어 반환된다.

**And When** 보안 담당자(특수 권한)가 동일 API를 호출하면
**Then** 평문이 반환된다 (감사로그 기록 동반).

### REQ-CROSS-004 — 감사로그 AOP

**Given** 임의 도메인 Service의 `create*`, `update*`, `delete*` 메서드가 호출되면
**When** 메서드가 정상 종료되거나 예외가 발생하면
**Then** `audit_log` 에 (user_id, ip, class_name, method_name, params_summary, result_code, elapsed_ms) 행이 자동 기록된다.

### REQ-CROSS-005 — 변조 방지

**Given** DB 운영 사용자(`cms_app`) 권한이 audit_log에 INSERT만 허용되고
**When** 누군가 `UPDATE audit_log SET ...` 또는 `DELETE FROM audit_log ...` 를 실행 시도하면
**Then** PostgreSQL 권한 오류로 실패한다.

### REQ-CROSS-006 — 다국어 메시지

**Given** 백엔드가 `messages_ko.properties`, `messages_en.properties` 를 로드한 상태에서
**When** 클라이언트가 `Accept-Language: en` 헤더로 검증 실패 응답을 받으면
**Then** 응답 메시지가 영어로 반환된다.

### REQ-CROSS-007 — 다국어 콘텐츠 테이블

**Given** `content_i18n` 테이블이 (content_id, lang) 복합키 제약을 보유하고
**When** 동일 (content_id=1, lang=ko) 행을 중복 INSERT 시도하면
**Then** 무결성 제약 위반(중복 키) 으로 실패한다.

### REQ-CROSS-008 — Docker 배포

**Given** 프로젝트 루트에서
**When** `docker-compose -f deploy/docker-compose.yml up -d` 를 실행하면
**Then** postgres + backend + admin-fe + public-fe + nginx 5개 컨테이너가 정상 기동되고
**And** `curl http://localhost:8080/actuator/health` 가 `{"status":"UP"}` 을 반환한다.

### REQ-CROSS-009 — 관측성

**Given** Spring Boot Actuator가 활성화된 상태에서
**When** 내부망에서 `GET /actuator/prometheus` 를 호출하면
**Then** Prometheus 형식 메트릭이 반환된다.

**And When** 외부망(nginx 차단 영역) 에서 동일 경로를 호출하면
**Then** 404 또는 403이 반환된다.

### REQ-CROSS-010 — 구조화 로그

**Given** 운영 프로파일(`prod`) 에서 시스템이 가동되고
**When** 임의의 API 요청이 처리되면
**Then** stdout 로그가 JSON 형식이며 필수 필드(`timestamp, level, traceId, message, logger`) 를 포함한다.

---

## F. 품질 게이트 (TRUST 5)

### QG-T (Tested) — 커버리지 게이트

**Given** CI 파이프라인이 `./gradlew jacocoTestReport` 와 `pnpm vitest run --coverage` 를 실행하면
**When** 커버리지 결과가 분석되면
**Then** Line/Branch 커버리지 모두 85% 이상이고, 미달 시 빌드가 실패한다.

### QG-R (Readable) — 린트 게이트

**Given** CI 파이프라인이 `./gradlew check` (Checkstyle/SpotBugs) 와 `pnpm eslint . --max-warnings=0` 를 실행하면
**When** 결과가 분석되면
**Then** 위반 0건이며, 미달 시 빌드가 실패한다.

### QG-U (Unified) — 포맷 일관성

**Given** CI에서 Spotless 또는 google-java-format / Prettier 가 실행되면
**When** 포맷 차이가 검출되면
**Then** 빌드가 실패한다.

### QG-S (Secured) — 의존성 취약점

**Given** CI에서 OWASP Dependency Check (BE) 와 `pnpm audit --audit-level=high` (FE) 가 실행되면
**When** Critical/High 취약점이 발견되면
**Then** 빌드가 실패한다.

### QG-Tr (Trackable) — 커밋 메시지

**Given** 작성된 커밋 메시지가
**When** Conventional Commits 정규식 `^(feat|fix|docs|chore|refactor|test|ci|build)(\(.+\))?: .+` 에 매칭되지 않으면
**Then** pre-commit hook이 커밋을 거부한다.

---

## G. Definition of Done (전체 SPEC 완료 기준)

본 Umbrella SPEC은 후속 SPEC-CMS-002~005가 모두 완료되었을 때 비로소 충족된 것으로 간주한다.

- 모든 REQ-AUTH-*, REQ-BOARD-*, REQ-CONTENT-*, REQ-SYSTEM-* 인수 조건이 자동화 테스트로 검증됨
- 모든 REQ-CROSS-* 가 CI 파이프라인에 게이트로 통합됨
- KWCAG 2.2 AA axe-core 검사 0건 위반
- JaCoCo + Vitest 커버리지 ≥ 85%
- OpenAPI 3.1 스펙이 자동 생성되며 Swagger UI에서 모든 엔드포인트가 노출됨
- docker-compose up 으로 전체 스택이 단일 명령에 기동됨
- 운영 매뉴얼 (관리자 가이드, 운영자 가이드) 이 한국어로 작성됨
