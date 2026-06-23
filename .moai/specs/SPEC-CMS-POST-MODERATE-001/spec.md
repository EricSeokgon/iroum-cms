# SPEC-CMS-POST-MODERATE-001: 게시글 관리자 모더레이션 패널

## Status: Completed

## Overview
관리자가 전체 게시판에 걸쳐 BBS 게시글을 모더레이션(숨김/복원/강제 삭제)할 수 있는 어드민 패널을 구현한다.
이미 `BbsPostStatus.HIDDEN`이 존재하지만 관리자가 교차 게시판으로 게시글을 열람하고 상태를 변경할 수 있는
백엔드 엔드포인트와 프론트엔드 뷰가 없다.

기존 패턴 참조:
- `SPEC-CMS-COMMENT-MODERATE-001` → `CommentAdminController` / `CommentManagementView.vue`
- `SPEC-CMS-QNA-MODERATE-001` → `QnaAdminController` / `QnaManagementView.vue`

## Scope

### Backend (Spring Boot 3 + MyBatis)

**신규 파일:**
- `PostAdminController.java` — `GET /api/v1/admin/posts`, `PATCH /api/v1/admin/posts/{id}/status`, `DELETE /api/v1/admin/posts/{id}`
- `PostAdminStatusRequest.java` — 상태 변경 요청 DTO
- `PostAdminService.java` — 서비스 인터페이스
- `PostAdminServiceImpl.java` — 서비스 구현체
- `PostAdminSummary.java` (record DTO) — 관리자 목록 응답

**수정 파일:**
- `BbsPostMapper.java` — `listForAdmin`, `countForAdmin`, `updateStatusByAdmin` 메서드 추가
- `BbsPostMapper.xml` — 위 메서드에 대한 SQL 추가

### Frontend (Vue 3 + Element Plus)

**신규 파일:**
- `frontend/admin/src/api/postAdmin.ts` — 관리자 게시글 API 래퍼
- `frontend/admin/src/views/board/PostManagementView.vue` — 관리자 모더레이션 뷰

**수정 파일:**
- `frontend/admin/src/router/index.ts` — 라우트 추가 (`board/posts/management`)
- `frontend/admin/src/locales/ko.json` — `postAdmin` 키 추가
- `frontend/admin/src/locales/en.json` — `postAdmin` 키 추가

## Requirements

### REQ-PA-001: 전체 게시글 목록 조회 (관리자)
WHEN 관리자가 `GET /api/v1/admin/posts`를 호출하면
THEN 모든 게시판의 게시글을 페이징하여 반환한다.
필터: `bbsId` (선택), `status` (선택), `keyword` (제목 검색, 선택), `page`, `size`

### REQ-PA-002: 게시글 상태 변경 (관리자)
WHEN 관리자가 `PATCH /api/v1/admin/posts/{id}/status`를 호출하면
THEN 해당 게시글 상태를 `status` 필드값으로 변경한다.
허용 상태값: `PUBLISHED`, `HIDDEN`, `DRAFT`
존재하지 않는 ID → 404 응답

### REQ-PA-003: 게시글 강제 삭제 (관리자)
WHEN 관리자가 `DELETE /api/v1/admin/posts/{id}`를 호출하면
THEN 해당 게시글을 소프트 삭제한다 (기존 `deletePost` SQL 재사용).
존재하지 않는 ID → 404 응답

### REQ-PA-004: 접근 제어
모든 admin 엔드포인트는 `@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")` (클래스 레벨) 적용.

### REQ-PA-005: 프론트엔드 모더레이션 뷰
게시글 목록, 게시판 필터, 상태 필터, 제목 키워드 검색, 숨기기/복원/삭제 액션, 페이지네이션, 
aria-live 알림 포함.

## Acceptance Criteria

| ID | 설명 |
|----|------|
| AC-PA-001 | `GET /api/v1/admin/posts` → 200, 페이지 응답 반환 |
| AC-PA-002 | `PATCH .../status` body `{status:HIDDEN}` → 상태 HIDDEN 으로 변경, 200 반환 |
| AC-PA-003 | `DELETE .../` → 소프트 삭제, 204 반환 |
| AC-PA-004 | 인증 없이 요청 → 401, MANAGER 아닌 사용자 → 403 |

## Technical Notes

- `PostAdminSummary` 필드: `id`, `bbsId`, `bbsName`, `title`, `authorId`, `status`, `createdAt`
- 목록 SQL은 `bbs_post p JOIN bbs_master m ON m.id = p.bbs_id` 로 게시판명 포함
- `BbsPostMapper.updateStatusByAdmin`은 `SET status = #{status}, updated_at = NOW()`
- 소프트 삭제는 기존 `BbsPostMapper.deletePost` SQL ID 재사용 (updateStatusByAdmin DELETED + deleted_at=NOW())
- 프론트엔드는 `CommentManagementView.vue` / `QnaManagementView.vue` 패턴 동일 적용
- `AuthorizationCoverageArchTest` baseline: 클래스 레벨 `@PreAuthorize` 이므로 메서드 레벨 카운트(126)에 영향 없음
