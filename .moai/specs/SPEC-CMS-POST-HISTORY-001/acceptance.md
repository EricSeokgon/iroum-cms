---
id: SPEC-CMS-POST-HISTORY-001
version: 0.1.0
status: Draft
created: 2026-06-10
updated: 2026-06-10
author: manager-spec
---

# SPEC-CMS-POST-HISTORY-001 — 인수 기준 (acceptance.md)

## 1. Given-When-Then 시나리오

### AC-PH-001 — 버전 목록 페이징 조회 (REQ-PH-001, 002)

- **Given** 게시글 `postId=42` 에 버전 1~5의 스냅샷이 `bbs_post_history` 에 적재되어 있고 관리자가 인증된 상태일 때
- **When** `GET /api/v1/board/posts/42/history?page=0&size=3` 를 호출하면
- **Then** version 5, 4, 3 순(내림차순)으로 3개 요소가 반환되고, 각 요소는 version·editorName·editedAt·editReason·title 을 포함하며 content_html 은 포함하지 않는다. 페이징 메타(totalElements=5, totalPages=2)가 정확하다.

### AC-PH-002 — 단건 버전 본문 조회 (REQ-PH-004)

- **Given** 게시글 `postId=42`, `version=3` 스냅샷이 존재할 때
- **When** `GET /api/v1/board/posts/42/history/3` 를 호출하면
- **Then** 해당 버전의 title 과 content_html 전체 본문 + editorName·editedAt·editReason 이 단건으로 반환된다.

### AC-PH-003 — 존재하지 않는 버전 (REQ-PH-005)

- **Given** 게시글 `postId=42` 에 version 5까지만 존재할 때
- **When** `GET /api/v1/board/posts/42/history/99` 를 호출하면
- **Then** HTTP 404가 반환되고 빈 본문이나 다른 게시글/버전의 데이터가 절대 반환되지 않는다.

### AC-PH-004 — 삭제된/NULL 수정자 안전 처리 (REQ-PH-003)

- **Given** version 2의 `edited_by` 가 NULL(또는 해당 사용자가 삭제됨)일 때
- **When** `GET /api/v1/board/posts/42/history?page=0&size=20` 를 호출하면
- **Then** version 2 항목이 누락 없이 포함되고 editorName 은 null(또는 합의된 기본 표시값)로 반환되며 서버 오류가 발생하지 않는다.

### AC-PH-005 — 비인증 접근 차단 (REQ-PH-006)

- **Given** 인증 토큰이 없는 요청일 때
- **When** `GET /api/v1/board/posts/42/history` 또는 `.../history/3` 을 호출하면
- **Then** 본문이 노출되지 않고 401/403으로 거부된다.

### AC-PH-006 — 관리자 UI 히스토리 탭 흐름 (REQ-PH-007, 008)

- **Given** 관리자가 `PostDetailView.vue` 에서 이력 5건이 있는 게시글을 보고 있을 때
- **When** 히스토리 탭을 선택하고 목록에서 version 3 행을 클릭하면
- **Then** 버전·수정자·수정 일시·수정 사유 컬럼의 페이징 표가 표시되고, 행 선택 시 version 3의 제목과 렌더링된 content_html 이 읽기 전용으로 표시된다.

### AC-PH-007 — 이력 없음 빈 상태 (REQ-PH-009)

- **Given** 수정된 적 없어 스냅샷이 0건인 게시글을 관리자가 볼 때
- **When** 히스토리 탭을 선택하면
- **Then** "수정 이력 없음" 빈 상태 메시지가 표시되고 오류·빈 표 깨짐 없이 동작한다.

### AC-PH-008 — read-only 보장 (REQ-PH-010)

- **Given** 관리자가 히스토리 탭/표/본문 영역을 보고 있을 때
- **When** 화면의 모든 컨트롤을 확인하면
- **Then** 복원(restore)·롤백·편집·삭제 등 어떤 mutating 액션 버튼/링크도 존재하지 않는다.

## 2. 엣지 케이스

- 동일 게시글에 매우 많은 버전(예: 100+) — 페이징이 깨지지 않고 마지막 페이지가 올바르게 반환된다.
- `postId` 자체가 존재하지 않음 — 빈 목록(또는 404, 백엔드 합의)으로 일관 처리하며 500이 발생하지 않는다.
- content_html 에 큰 본문/특수문자/이미지 태그 — 관리자 렌더링 시 sanitization 일관성이 유지되어 XSS가 발생하지 않는다.
- `size` 0 또는 음수, `page` 음수 등 비정상 파라미터 — 안전한 기본값 처리 또는 400.

## 3. 품질 게이트 (TRUST 5)

- **Tested**: 매퍼(페이징 정렬/JOIN), 서비스(404 예외), 컨트롤러(인증/응답) 단위 테스트. 신규 코드 커버리지 충족.
- **Readable**: DTO/메서드 명확 명명, 한국어 주석(code_comments=ko).
- **Unified**: 기존 board 도메인·`PageResponse` 패턴 일관 준수.
- **Secured**: 비인증 차단(AC-PH-005), content_html sanitization 유지, 다른 게시글 데이터 누출 없음(AC-PH-003).
- **Trackable**: 커밋·코드에 SPEC-CMS-POST-HISTORY-001 / REQ-PH-* 참조.

## 4. Definition of Done

- [ ] `GET /api/v1/board/posts/{postId}/history` 페이징 목록 동작 (AC-PH-001)
- [ ] `GET /api/v1/board/posts/{postId}/history/{version}` 단건 본문 동작 (AC-PH-002)
- [ ] 미존재 버전 404 (AC-PH-003), NULL 수정자 안전(AC-PH-004), 비인증 차단(AC-PH-005)
- [ ] `PostDetailView.vue` 히스토리 탭: 목록 → 버전 본문 흐름 (AC-PH-006), 빈 상태(AC-PH-007), read-only(AC-PH-008)
- [ ] ko/en i18n 키 추가
- [ ] 신규 DB 마이그레이션 0건 (테이블·인덱스 V10 재사용)
- [ ] 이력 적재(write) 로직 무변경 확인
- [ ] 단위 테스트 GREEN, CI GREEN
