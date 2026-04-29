# SPEC-CMS-003 Acceptance Criteria — Bundle B (게시판·공지·Q&A·FAQ)

> 본 문서는 spec.md의 모든 REQ-BOARD-*-D-* 요구사항에 대응하는 Given/When/Then 형식의 인수 조건을 정의한다. 각 항목은 JUnit 5 + MockMvc + Testcontainers(PostgreSQL) 통합 테스트 또는 Vitest/Playwright(FE) 로 자동 검증된다.

---

## A. 게시판 마스터 CRUD (REQ-BOARD-001-D-*)

### A-01 (REQ-BOARD-001-D-1) 마스터 생성 성공

**Given** SYSADMIN 권한 사용자가 인증된 상태이고
**When** `POST /api/v1/boards`에 `{code:"notice_general", name:"일반공지", type:"NOTICE", useComment:false, useAttachment:true, maxAttachmentCount:5, maxAttachmentSizeKb:10240, allowAnonymous:false, allowSecret:false, pageSize:20}`을 보내면
**Then** 201 Created + `{id, code:"notice_general", ...}`이 반환되고
**And** `bbs_master` 테이블에 status='ACTIVE' 행이 추가된다.

### A-02 (REQ-BOARD-001-D-1) 마스터 code 중복

**Given** code='notice_general' 마스터가 이미 존재하고
**When** 동일 code로 POST를 시도하면
**Then** 409 Conflict + `BOARD_CODE_DUPLICATE`가 반환된다.

### A-03 (REQ-BOARD-001-D-1) type 화이트리스트 위반

**When** type='UNKNOWN'으로 POST를 시도하면
**Then** 400 Bad Request + `BOARD_TYPE_INVALID`가 반환된다 (DB CHECK constraint 충돌 전 검증).

### A-04 (REQ-BOARD-001-D-1) 첨부 한도 초과

**When** maxAttachmentSizeKb=999999(>102400)로 POST를 시도하면
**Then** 400 Bad Request + `BOARD_LIMIT_EXCEEDED`가 반환된다.

### A-05 (REQ-BOARD-001-D-2) code/type 변경 거부

**Given** 마스터 id=12가 존재하고
**When** `PUT /api/v1/boards/12`로 `{code:"changed", type:"FAQ"}`을 보내면
**Then** 400 Bad Request + `BOARD_FIELD_IMMUTABLE`이 반환된다.

### A-06 (REQ-BOARD-001-D-3) soft 비활성화

**When** SYSADMIN이 `DELETE /api/v1/boards/12`를 호출하면
**Then** 204 No Content + `bbs_master.status='INACTIVE'`로 갱신되고, 기존 게시글은 삭제되지 않는다.

### A-07 (REQ-BOARD-001-D-4) use_comment=false 정책

**Given** 마스터 use_comment=false인 게시글이 존재하고
**When** 사용자가 댓글 POST를 시도하면
**Then** 400 + `BOARD_COMMENT_DISABLED`가 반환된다.

### A-08 (REQ-BOARD-001-D-5) 다국어 마스터명

**Given** 마스터 metadata.i18n_name = `{"ko":"공지","en":"Notices"}`
**When** `Accept-Language: en`으로 마스터 조회 시
**Then** name 필드는 "Notices"로 반환된다.

---

## B. 게시글 CRUD (REQ-BOARD-002-D-*)

### B-01 (REQ-BOARD-002-D-1) 게시글 작성 정상

**Given** USER 권한 사용자가 인증되고 마스터 bbsId=10이 ACTIVE이며
**When** `POST /api/v1/boards/10/posts`에 `{title:"안내", contentHtml:"<p>본문</p>"}`을 보내면
**Then** 201 + 게시글이 반환되고 `bbs_post.content_text='본문'`(HTML 제거됨)으로 저장된다.

### B-02 (REQ-BOARD-002-D-1) XSS 시도 sanitize

**When** `contentHtml="<script>alert(1)</script><p>safe</p><img src=x onerror=alert(2)>"`로 POST하면
**Then** 201이 반환되고 저장된 content_html에는 `<script>` 태그와 `onerror` 속성이 모두 제거되어 있고, `<img src="x">`만 남는다 (또는 외부 URL이면 제거).

### B-03 (REQ-BOARD-002-D-1) javascript: URL 차단

**When** `contentHtml='<a href="javascript:alert(1)">link</a>'`로 POST하면
**Then** 저장된 본문에서 `href` 속성이 제거되거나 `<a>` 태그 자체가 제거된다.

### B-04 (REQ-BOARD-002-D-2) 페이징·정렬 정상

**Given** bbs_post에 30건 게시글이 존재하고
**When** `GET /api/v1/boards/10/posts?page=0&size=10&sort=createdAt,desc&keyword=안내`를 호출하면
**Then** 200 + `{content:[10건], totalElements:N, totalPages:M, first:true, last:false}` 반환된다.

### B-05 (REQ-BOARD-002-D-2) 공지 분리 노출

**Given** is_notice=true이고 notice_until이 미래인 게시글 2건과 일반 게시글 5건이 존재하고
**When** `GET /api/v1/boards/10/posts?page=0&size=20`을 호출하면
**Then** 응답에 `notices:[2건]`과 `content:[5건]`이 분리되어 반환된다.

### B-06 (REQ-BOARD-002-D-3) 게시글 상세 + view_count 증가

**Given** 게시글 id=100, view_count=5인 상태에서
**When** 사용자가 `GET /api/v1/posts/100`을 처음 호출하면
**Then** 200 + 본문이 반환되고 view_count=6으로 증가한다.

### B-07 (REQ-BOARD-002-D-3) view_log dedupe (1시간 내 중복)

**Given** 동일 사용자가 5분 전 같은 게시글을 조회한 상태에서
**When** 동일 사용자가 같은 게시글을 다시 조회하면
**Then** view_count는 변하지 않는다.

### B-08 (REQ-BOARD-002-D-3) 비공개 게시글 타인 접근

**Given** is_secret=true 게시글의 작성자가 아닌 일반 사용자가
**When** 해당 게시글 상세를 요청하면
**Then** 404 Not Found + `POST_NOT_FOUND`가 반환된다 (403 대신 존재 자체 숨김).

### B-09 (REQ-BOARD-002-D-4) 게시글 수정 + 이력 보존

**Given** 게시글 id=100이 v1 상태로 존재하고
**When** 작성자가 `PUT /api/v1/posts/100`로 title·content를 수정하면
**Then** 200 반환 + `bbs_post_history` 테이블에 v1 본문이 보존되고 bbs_post는 업데이트된다.

### B-10 (REQ-BOARD-002-D-5) 게시글 soft delete

**When** 작성자가 `DELETE /api/v1/posts/100`을 호출하면
**Then** 204 + `bbs_post.status='DELETED', deleted_at=now`로 갱신되고
**And** 일반 사용자의 목록·상세 조회에서 제외된다.

---

## C. 댓글 (REQ-BOARD-003-D-*)

### C-01 (REQ-BOARD-003-D-1) 댓글 등록 정상

**Given** use_comment=true 게시판의 게시글 id=100
**When** `POST /api/v1/posts/100/comments`에 `{content:"좋은 글입니다"}`를 보내면
**Then** 201 + 댓글이 반환되고 `bbs_post.comment_count`가 1 증가한다.

### C-02 (REQ-BOARD-003-D-2) 1단계 대댓글 정상

**Given** 부모 댓글 id=200(parent_comment_id=NULL)
**When** `POST /api/v1/posts/100/comments`에 `{content:"답글", parentCommentId:200}`을 보내면
**Then** 201이 반환되고 새 댓글의 parent_comment_id=200이 된다.

### C-03 (REQ-BOARD-003-D-2) 대대댓글 차단

**Given** 댓글 id=300이 parent_comment_id=200(이미 대댓글)
**When** `POST /api/v1/posts/100/comments`에 `parentCommentId=300`을 보내면
**Then** 400 + `COMMENT_DEPTH_EXCEEDED`가 반환된다 (trigger 또는 service 검증).

### C-04 (REQ-BOARD-003-D-3) 댓글 수정 1시간 정책

**Given** 댓글 id=200이 2시간 전 작성됨
**When** 작성자가 `PUT /api/v1/comments/200`로 수정 시도하면
**Then** 403 + `COMMENT_EDIT_WINDOW_EXPIRED`가 반환된다.

### C-05 (REQ-BOARD-003-D-4) 댓글 soft delete + 마스킹

**When** 작성자가 `DELETE /api/v1/comments/200`을 호출하면
**Then** 204 + 응답 시 content가 "삭제된 댓글입니다"로 마스킹되고, 자식 대댓글은 유지된다.

### C-06 (REQ-BOARD-003-D-5) 익명 댓글 등록

**Given** 게시판 allow_anonymous=true이고
**When** 비인증 클라이언트가 `POST /api/v1/posts/100/comments`에 `{content, anonymousName:"홍길동", anonymousPassword:"abc123!"}`를 보내면
**Then** 201이 반환되고 `bbs_comment.author_id IS NULL, anonymous_pwd_hash`에 BCrypt 해시가 저장된다.

### C-07 (REQ-BOARD-003-D-5) 익명 댓글 본인 삭제 (비번 검증)

**Given** 익명 댓글 id=400이 저장된 상태에서
**When** `DELETE /api/v1/comments/400?password=abc123!`을 호출하면
**Then** 204 + soft delete된다.

**And When** 잘못된 비번으로 호출하면
**Then** 403 + `ANONYMOUS_PASSWORD_MISMATCH`가 반환된다.

---

## D. 첨부파일 업로드·검증 (REQ-BOARD-004-D-*)

### D-01 (REQ-BOARD-004-D-1) 확장자 화이트리스트 차단

**When** 사용자가 `.exe` 파일을 `POST /api/v1/attachments/init`로 업로드하면
**Then** 400 + `FILE_EXTENSION_NOT_ALLOWED`가 반환된다.

### D-02 (REQ-BOARD-004-D-2) MIME 위장 차단

**Given** `.txt`로 확장자를 위장한 PE32 실행파일
**When** 업로드 시도하면
**Then** 400 + `FILE_MIME_MISMATCH`가 반환된다 (Tika 매직넘버 검증).

### D-03 (REQ-BOARD-004-D-3) 크기 초과

**Given** 게시판 max_attachment_size_kb=10240
**When** 11MB(11264KB) 파일을 업로드하면
**Then** 413 + `FILE_SIZE_EXCEEDED`가 반환된다.

### D-04 (REQ-BOARD-004-D-4) 파일명 sanitize

**Given** 업로드 파일명이 `../../etc/passwd.txt`인 경우
**When** 업로드 시도하면
**Then** stored_path가 webroot 외부 디렉토리의 UUID 기반 경로로 저장되고, file_name 컬럼은 sanitized된 안전 문자열만 보존된다.

### D-05 (REQ-BOARD-004-D-4) NULL byte 제거

**Given** 파일명에 NULL 바이트(`\0`)가 포함된 경우
**When** 업로드 시도하면
**Then** NULL 바이트가 제거된 파일명으로 저장되거나, 결과 파일명이 비면 400 `FILE_NAME_INVALID`가 반환된다.

### D-06 (REQ-BOARD-004-D-5) 정상 업로드 → PENDING

**Given** 정상 PDF 파일(<10MB)
**When** 업로드하면
**Then** 201 + `{attachmentId, scanStatus:"PENDING"}` 반환되고
**And** `bbs_attachment.scan_status='PENDING'`, SHA-256 checksum이 저장되며 비동기 큐에 enqueue된다.

### D-07 (REQ-BOARD-004-D-5) 다중 첨부 한도

**Given** 게시판 max_attachment_count=5이고 이미 5개 첨부된 게시글에서
**When** 6번째 첨부 시도하면
**Then** 400 + `FILE_COUNT_EXCEEDED`가 반환된다 (게시글 작성·수정 시점 검증).

### D-08 zip bomb 방어

**Given** 압축 비율 1:1000 이상의 .zip 파일
**When** 업로드 시도하면
**Then** 400 + `FILE_ARCHIVE_RATIO_EXCEEDED`가 반환된다.

---

## E. 첨부파일 다운로드 (REQ-BOARD-005-D-*)

### E-01 (REQ-BOARD-005-D-1) 서명 URL 발급 정상

**Given** scan_status='CLEAN'인 첨부 id=500의 게시글 권한을 사용자가 보유
**When** `POST /api/v1/attachments/500/download-url`을 호출하면
**Then** 200 + `{url:"...?token=&expires=&sig=", expiresAt}` 응답되고 expiresAt = now + 15분 ± 1초 이내이다.

### E-02 (REQ-BOARD-005-D-2) HMAC 검증 통과

**When** 발급된 URL로 `GET /api/v1/attachments/500/download?...&sig=정상`을 호출하면
**Then** 200 + Content-Disposition 헤더(`attachment; filename*=UTF-8''...`)와 함께 파일 스트림이 반환된다.

### E-03 (REQ-BOARD-005-D-2) HMAC 변조 차단

**When** sig 파라미터를 변조한 URL로 호출하면
**Then** 403 + `SIGNATURE_INVALID`가 반환된다.

### E-04 (REQ-BOARD-005-D-2) 만료된 URL

**Given** expires가 과거인 URL
**When** 호출하면
**Then** 403 + `SIGNATURE_EXPIRED`가 반환된다.

### E-05 (REQ-BOARD-005-D-3) 한글 파일명 RFC 5987

**Given** 파일명이 "보고서.pdf"인 첨부
**When** 다운로드 요청 시
**Then** 응답 헤더에 `Content-Disposition: attachment; filename*=UTF-8''%EB%B3%B4%EA%B3%A0%EC%84%9C.pdf`가 포함된다.

### E-06 (REQ-BOARD-005-D-3) download_count 증가

**When** 다운로드 성공 시
**Then** `bbs_attachment.download_count`가 1 증가한다.

### E-07 (REQ-BOARD-005-D-4) 감사로그 적재

**When** 다운로드 요청이 처리되면
**Then** `audit_log`에 (class=AttachmentService, method=download, attachmentId, userId, ip)이 기록된다.

### E-08 (REQ-BOARD-005-D-5) 스캔 PENDING 거부

**Given** scan_status='PENDING'인 첨부
**When** download-url 발급 요청 시
**Then** 423 + `FILE_NOT_READY`가 반환된다.

### E-09 (REQ-BOARD-005-D-5) INFECTED 거부

**Given** scan_status='INFECTED'
**When** download-url 발급 요청 시
**Then** 451 + `FILE_INFECTED`가 반환된다.

### E-10 비공개 게시글 첨부 권한

**Given** is_secret=true인 게시글의 첨부 id=600을 작성자가 아닌 사용자가
**When** download-url 요청하면
**Then** 404 + `POST_NOT_FOUND` (게시글 자체를 숨김)이 반환된다.

---

## F. 공지사항 (REQ-BOARD-006-D-*)

### F-01 (REQ-BOARD-006-D-1) 공지 등록 정상

**Given** CONTENT_ADMIN이 인증되고
**When** `POST /api/v1/boards/10/posts`에 `{title, contentHtml, isNotice:true, noticeFrom:"2026-04-29T00:00Z", noticeUntil:"2026-05-06T00:00Z"}`를 보내면
**Then** 201 + 게시글이 등록되고 is_notice=true, notice_from/until이 저장된다.

### F-02 (REQ-BOARD-006-D-1) 노출 기간 역전 거부

**When** noticeFrom > noticeUntil로 POST 시도하면
**Then** 400 + `NOTICE_PERIOD_INVALID` 또는 DB CHECK constraint 위반으로 실패한다.

### F-03 (REQ-BOARD-006-D-2) 활성 공지 상단 노출

**Given** notice_from <= now < notice_until인 공지가 존재
**When** `GET /api/v1/boards/10/posts?page=0`을 호출하면
**Then** 응답의 `notices[]`에 해당 공지가 포함된다.

### F-04 (REQ-BOARD-006-D-3) 만료 공지 일반 정렬

**Given** notice_until이 어제로 설정된 공지
**When** 목록 조회 시
**Then** 해당 공지는 `notices[]`에서 제외되고 일반 `content[]`에 createdAt 정렬로 포함된다.

### F-05 (REQ-BOARD-006-D-4) NORMAL 게시판에서 공지 등록

**Given** type='NORMAL' 게시판에서 CONTENT_ADMIN이
**When** isNotice=true로 게시글 등록하면
**Then** 201이 반환되고 마스터 type과 무관하게 individual notice로 등록된다.

### F-06 (REQ-BOARD-006-D-1) 일반 사용자 공지 등록 차단

**Given** USER 역할 사용자가
**When** isNotice=true로 POST 시도하면
**Then** 403 + `AUTH_PERMISSION_DENIED`가 반환된다.

---

## G. FAQ (REQ-BOARD-007-D-*)

### G-01 (REQ-BOARD-007-D-1) FAQ 등록

**Given** CONTENT_ADMIN이
**When** `POST /api/v1/faqs`에 `{categoryCode:"ACCOUNT", question:"비번 찾기?", answerHtml:"<p>안내</p>", sortOrder:1}`를 보내면
**Then** 201 + FAQ 저장(answer sanitize 적용)된다.

### G-02 (REQ-BOARD-007-D-2) 카테고리 조회

**Given** category=ACCOUNT FAQ 5건, ACCESS 3건이 존재
**When** `GET /api/v1/faqs?category=ACCOUNT&page=0&size=10`을 호출하면
**Then** 200 + 해당 카테고리 5건이 sort_order ASC 정렬로 반환된다.

### G-03 (REQ-BOARD-007-D-3) FAQ 검색

**Given** question에 "비밀번호" 포함된 FAQ 3건
**When** `GET /api/v1/faqs?keyword=비밀번호`를 호출하면
**Then** trigram 부분일치로 3건 모두 반환된다.

### G-04 (REQ-BOARD-007-D-4) 정렬 일괄 변경

**When** `PUT /api/v1/faqs/reorder`에 `[{id:1,sortOrder:3},{id:2,sortOrder:1},{id:3,sortOrder:2}]`를 보내면
**Then** 200 + 단일 트랜잭션으로 모든 sort_order가 갱신된다.

### G-05 FAQ soft delete

**When** `DELETE /api/v1/faqs/1`을 호출하면
**Then** 204 + status='DELETED', deleted_at=now가 적용된다.

### G-06 카테고리별 카운트

**When** `GET /api/v1/faqs/categories`를 호출하면
**Then** 200 + `[{categoryCode:"ACCOUNT", count:5}, {categoryCode:"ACCESS", count:3}]`이 반환된다.

---

## H. Q&A (REQ-BOARD-008-D-*)

### H-01 (REQ-BOARD-008-D-1) 질문 등록 PENDING

**Given** USER 인증
**When** `POST /api/v1/qnas`에 `{title, questionHtml, isPrivate:false}`를 보내면
**Then** 201 + status='PENDING', questioner_id=현재사용자로 저장된다.

### H-02 (REQ-BOARD-008-D-2) 답변 등록 → ANSWERED

**Given** PENDING Q&A id=700 + CONTENT_ADMIN 인증
**When** `POST /api/v1/qnas/700/answer`에 `{answerHtml:"<p>답변</p>"}`을 보내면
**Then** 200 + status='ANSWERED', answered_at, answerer_id가 갱신된다.

### H-03 (REQ-BOARD-008-D-2) 이미 답변된 Q&A 재답변 거부

**Given** Q&A id=700 status=ANSWERED
**When** 다시 답변 POST하면
**Then** 409 + `QNA_ALREADY_ANSWERED`가 반환된다.

### H-04 (REQ-BOARD-008-D-3) 비공개 Q&A 본인 조회

**Given** is_private=true Q&A id=701, questioner_id=user_A
**When** user_A가 `GET /api/v1/qnas/701`을 호출하면
**Then** 200 + 상세가 반환된다.

### H-05 (REQ-BOARD-008-D-3) 비공개 Q&A 타인 조회 → 404

**Given** is_private=true Q&A id=701, questioner_id=user_A
**When** user_B(USER)가 `GET /api/v1/qnas/701`을 호출하면
**Then** 404 + `QNA_NOT_FOUND`가 반환된다 (403 대신 존재 숨김).

### H-06 (REQ-BOARD-008-D-3) 비공개 Q&A 운영자 조회

**Given** is_private=true Q&A
**When** CONTENT_ADMIN이 조회하면
**Then** 200 + 상세가 반환된다.

### H-07 (REQ-BOARD-008-D-4) 답변 시 인앱 알림 적재

**When** Q&A 답변이 등록되면
**Then** notification 큐 또는 테이블에 `{userId:questionerId, type:'QNA_ANSWERED', qnaId}`가 적재된다.

### H-08 (REQ-BOARD-008-D-4) SMTP 비활성 시 이메일 스킵

**Given** spring.mail.host가 미설정
**When** Q&A 답변 등록 시
**Then** 인앱 알림만 적재되고 SMTP 호출은 발생하지 않는다 (조건부 워커).

### H-09 (REQ-BOARD-008-D-5) 종결

**Given** ANSWERED Q&A
**When** questioner가 `POST /api/v1/qnas/{id}/close`를 호출하면
**Then** 200 + status='CLOSED'로 갱신되고 추가 답변이 거부된다.

---

## I. 검색 (REQ-BOARD-009-D-*)

### I-01 (REQ-BOARD-009-D-1) FTS 검색

**Given** content_text="공공 데이터 활용"인 게시글 1건
**When** `GET /api/v1/search?q=공공&types=POST`를 호출하면
**Then** 200 + 해당 게시글이 results에 포함된다.

### I-02 (REQ-BOARD-009-D-1) keyword 길이 부족

**When** `?q=가&types=POST` (길이 1자) 호출 시
**Then** 400 + `SEARCH_KEYWORD_TOO_SHORT`가 반환된다.

### I-03 (REQ-BOARD-009-D-2) 카테고리 필터

**Given** category_code='NEWS' 게시글 3건과 'EVENT' 5건
**When** `GET /api/v1/boards/10/posts?category=NEWS`를 호출하면
**Then** 200 + content[].length=3이 반환된다.

### I-04 (REQ-BOARD-009-D-3) 기간 필터

**When** `?from=2026-04-01&to=2026-04-29`로 호출하면
**Then** 해당 기간 내 게시글만 반환된다.

### I-05 (REQ-BOARD-009-D-4) 작성자 필터

**When** `?authorId=42`로 호출하면
**Then** author_id=42 게시글만 반환된다.

### I-06 (REQ-BOARD-009-D-5) 하이라이트 (옵션)

**When** `?q=공공&highlight=true`로 호출하면
**Then** 응답의 snippet 필드에 `<mark>공공</mark>`이 포함된다.

---

## J. 페이징·정렬 (REQ-BOARD-010-D-*)

### J-01 (REQ-BOARD-010-D-1) 기본 페이지

**When** size 미지정 호출 시
**Then** 게시판 마스터의 page_size 또는 20이 적용된다.

### J-02 (REQ-BOARD-010-D-1) size 한도

**When** `?size=200`으로 호출 시
**Then** 400 + `PAGE_SIZE_EXCEEDED` 또는 size=100으로 강제된다 (정책 결정: 거부).

### J-03 (REQ-BOARD-010-D-2) 정렬 화이트리스트

**When** `?sort=password,asc`로 호출 시
**Then** 400 + `INVALID_SORT_FIELD`가 반환된다 (SQL Injection 방어).

### J-04 (REQ-BOARD-010-D-2) 정상 정렬

**When** `?sort=viewCount,desc`로 호출 시
**Then** 200 + view_count DESC 정렬 결과가 반환된다.

### J-05 (REQ-BOARD-010-D-3) 응답 메타

**When** 임의 목록 호출 시
**Then** 응답에 `{content, page, size, totalElements, totalPages, first, last}`가 모두 포함된다.

---

## K. 권한 매트릭스 검증

### K-01 익명 사용자의 비공개 게시판 차단

**Given** role_required_read='USER'인 게시판
**When** 익명 클라이언트가 목록 조회 시
**Then** 401 + `AUTH_REQUIRED`가 반환된다.

### K-02 USER가 마스터 생성 시도

**When** USER 역할이 `POST /api/v1/boards`를 호출하면
**Then** 403 + `AUTH_PERMISSION_DENIED`가 반환된다.

### K-03 작성자 외 게시글 수정 차단

**Given** 게시글 작성자가 user_A
**When** user_B(USER)가 `PUT /api/v1/posts/{id}` 호출 시
**Then** 403이 반환된다.

### K-04 CONTENT_ADMIN의 타인 게시글 수정

**When** CONTENT_ADMIN이 user_A의 게시글을 수정하면
**Then** 200 + 수정 성공한다 (운영자 권한).

---

## L. 보안·횡단 시나리오

### L-01 SQL Injection 정렬

**When** `?sort=createdAt; DROP TABLE bbs_post;--`로 호출 시
**Then** 400 + `INVALID_SORT_FIELD` (화이트리스트 거부, SQL 미실행).

### L-02 스크립트 본문 실제 렌더링 시 미실행

**Given** 사용자가 `<img src=x onerror=alert(1)>` 본문 등록 시도
**When** sanitize 후 응답 본문에 onerror 속성 미포함
**Then** Playwright가 console.log 검사 시 alert 발생 없음.

### L-03 다운로드 권한 우회 시도

**Given** is_secret=true 게시글의 첨부 id=600
**When** 권한 없는 user_B가 download-url 직접 호출 시
**Then** 404 + `POST_NOT_FOUND` (존재 자체 숨김).

### L-04 INFECTED 파일 자동 격리

**Given** ClamAV가 INFECTED 판정 후
**When** 게시글 응답에서 해당 첨부 메타 조회 시
**Then** scan_status='INFECTED' + download-url 발급 거부 + 격리 디렉터리로 이동되어 있다.

### L-05 댓글 RateLimit

**When** 같은 사용자가 1분 내 6번째 댓글 등록 시
**Then** 429 + `RATE_LIMIT_EXCEEDED`가 반환된다.

---

## M. 품질 게이트 (Bundle B 전용)

### QG-B-1 (Secured) — 보안

**Given** Playwright + 백엔드 통합 테스트가 실행되면
**When** XSS payload 100개 + 파일 위장 시나리오 + 만료 서명 URL 시도가 모두 수행되면
**Then** XSS 차단율 100%, MIME 위장 차단율 100%, 만료 URL 차단율 100%, 서명 URL TTL ≤ 15분이다.

### QG-B-2 (Performant) — 성능

**Given** JMeter 시나리오 (50 동시 사용자)
**When** 1만 건 게시판 데이터 + 10만 건 검색 인덱스에서 실행하면
**Then** 게시글 목록 p95 < 300ms, 상세 p95 < 200ms, 검색 p95 < 500ms, 다운로드 시작 < 500ms를 모두 충족한다.

### QG-B-3 (Accessible) — 접근성

**Given** Playwright + axe-core
**When** 게시판 목록·상세·작성·검색·FAQ·Q&A 화면을 모두 검사하면
**Then** KWCAG 2.2 AA critical/serious 위반 0건, 키보드 네비게이션 100% 가능, 페이징·표 ARIA 속성 정상이다.

### QG-B-4 (Trackable) — 감사

**Given** 게시글 작성·수정·삭제·다운로드 100건 시나리오
**When** audit_log를 조회하면
**Then** 모든 행위가 (userId, ip, class_name, method_name) 메타와 함께 기록되어 있고, 카운트 일치율 100%이다.

### QG-B-5 (Tested + Data Integrity) — 데이터

**Given** 첨부파일 1000건 업로드 + 다운로드 시나리오
**When** SHA-256 checksum을 다운로드 후 재계산하면
**Then** 무결성 일치율 100%이고, JaCoCo line/branch coverage ≥ 85%이다.

---

## N. Definition of Done

- 모든 REQ-BOARD-001-D ~ REQ-BOARD-010-D sub-REQ가 자동화 테스트로 검증됨 (35개 sub-REQ → 약 70개 G/W/T)
- 5개 품질 게이트(QG-B-1~5) 통과
- OWASP HTML Sanitizer 정책이 PolicyFactory 단위 테스트로 검증됨
- 첨부파일 업로드/다운로드/스캔 흐름이 Testcontainers + Mock SMTP/ClamAV로 통합 테스트됨
- PostgreSQL FTS 인덱스(GIN tsvector + pg_trgm)가 Flyway V*로 생성되어 있음
- 권한 캐시(SPEC-CMS-002 §5.8) 무효화가 게시판 권한 변경 시 정상 동작함
- audit_log 적재가 모든 C/U/D + 다운로드 경로에서 100% 동작함
- 기존 SPEC-CMS-002 인증·권한 테스트가 회귀 없이 통과함
