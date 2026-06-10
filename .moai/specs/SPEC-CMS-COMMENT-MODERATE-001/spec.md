---
id: SPEC-CMS-COMMENT-MODERATE-001
title: "댓글 관리자 모더레이션"
status: Implemented
version: 1.0.0
created_at: 2026-06-10
updated_at: 2026-06-10
---

# SPEC-CMS-COMMENT-MODERATE-001: 댓글 관리자 모더레이션

## 1. 개요

`bbs_comment` 테이블에는 `status CHECK (status IN ('VISIBLE','HIDDEN','DELETED'))` 제약이 있으나, 관리자가 댓글 상태를 관리할 API와 전용 관리 UI가 없다. 이 SPEC은 관리자가 시스템 전체 댓글을 조회하고 모더레이션(숨김 처리, 삭제)할 수 있는 기능을 추가한다.

### 갭 분석

| 계층 | 현황 | 필요 |
|------|------|------|
| DB | `bbs_comment.status` 컬럼 존재 | 변경 없음 |
| Backend API | `/api/v1/board/posts/{postId}/comments` (게시물별 조회만) | 전체 목록 조회 + 상태 변경 Admin API |
| Frontend | `PostCommentSection.vue` (PostDetailView 내 임베드) | 독립 댓글 관리 뷰 |

## 2. 요구사항 (EARS 형식)

### REQ-CMTM-001: 전체 댓글 목록 조회

> **WHEN** 관리자가 댓글 목록을 요청하면, **THE SYSTEM SHALL** 모든 게시판의 댓글을 페이지네이션하여 반환한다.

- API: `GET /api/v1/admin/comments?page=0&size=20&sort=createdAt,desc`
- 응답: `Page<CommentAdminSummary>` (id, postId, postTitle, boardName, authorUsername, content(일부), status, createdAt)
- 기본 정렬: `createdAt DESC`

### REQ-CMTM-002: 댓글 필터링

> **WHEN** 관리자가 필터 조건을 포함하여 댓글 목록을 요청하면, **THE SYSTEM SHALL** 해당 조건에 맞는 댓글만 반환한다.

- 지원 필터: `boardId` (게시판), `status` (ALL/VISIBLE/HIDDEN/DELETED), `keyword` (content 포함 검색)
- 필터 미지정 시 ALL 상태 포함 (DELETED 제외 기본값은 선택 가능하게)

### REQ-CMTM-003: 댓글 상태 변경 (숨김/공개)

> **WHEN** 관리자가 댓글 상태 변경을 요청하면, **THE SYSTEM SHALL** 해당 댓글의 status를 변경하고 변경된 댓글 정보를 반환한다.

- API: `PATCH /api/v1/admin/comments/{id}/status`
- 요청 body: `{ "status": "HIDDEN" | "VISIBLE" }`
- DELETED → VISIBLE/HIDDEN 변환 불가 (이미 삭제된 댓글은 복구 불가)

### REQ-CMTM-004: 댓글 강제 삭제

> **WHEN** 관리자가 댓글 삭제를 요청하면, **THE SYSTEM SHALL** 해당 댓글의 status를 DELETED로 설정하고 deleted_at을 기록한다.

- API: `DELETE /api/v1/admin/comments/{id}`
- 소프트 삭제 (status='DELETED', deleted_at=NOW())
- 이미 DELETED인 경우 idempotent (204 반환)

### REQ-CMTM-005: 비인가 접근 차단

> **WHEN** ADMIN 또는 MANAGER 권한이 없는 사용자가 admin 댓글 API에 접근하면, **THE SYSTEM SHALL** 403 Forbidden을 반환한다.

### REQ-CMTM-006: 프론트엔드 댓글 관리 뷰

> **WHEN** 관리자가 댓글 관리 메뉴에 접근하면, **THE SYSTEM SHALL** 페이지네이션된 댓글 목록과 필터 컨트롤을 표시하고, 각 댓글에 상태 변경/삭제 액션을 제공한다.

- 라우트: `/board/comments`
- 뷰: `CommentManagementView.vue`
- 기능: 게시판 필터 드롭다운, 상태 필터 드롭다운, 키워드 검색, 상태 뱃지, 숨김/공개/삭제 버튼

## 3. 인수 기준

### AC-CMTM-001: 전체 댓글 목록 API
- `GET /api/v1/admin/comments` → 200 OK, `Page<CommentAdminSummary>` 반환
- `totalElements`, `totalPages` 포함

### AC-CMTM-002: 필터 동작
- `?status=HIDDEN` → HIDDEN 상태 댓글만 반환
- `?keyword=테스트` → content에 '테스트' 포함하는 댓글만 반환
- `?boardId=1` → 해당 게시판 게시물의 댓글만 반환

### AC-CMTM-003: 상태 변경 API
- `PATCH /api/v1/admin/comments/{id}/status` body `{"status":"HIDDEN"}` → 200, 상태 HIDDEN
- DELETED 댓글에 VISIBLE 변경 요청 → 400 Bad Request

### AC-CMTM-004: 강제 삭제 API
- `DELETE /api/v1/admin/comments/{id}` → 204 No Content, status=DELETED
- 재삭제 요청 → 204 No Content (idempotent)

### AC-CMTM-005: 인증 체크
- 인증 없는 요청 → 401 Unauthorized
- USER 권한 토큰으로 요청 → 403 Forbidden

### AC-CMTM-006: 프론트엔드 표시
- 댓글 목록 테이블: 게시판명, 게시물 제목, 작성자, 내용(50자), 상태, 작성일 컬럼
- 상태별 뱃지: VISIBLE(초록), HIDDEN(주황), DELETED(빨강)
- 숨김/공개 토글 버튼 + 삭제 버튼

## 4. 기술 접근

### Backend

```
POST /api/v1/admin/comments?page&size&sort&boardId&status&keyword
GET  /api/v1/admin/comments
PATCH /api/v1/admin/comments/{id}/status   body: {status}
DELETE /api/v1/admin/comments/{id}
```

**새로 생성할 파일**:
- `CommentAdminController.java` — `@PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")`
- `CommentAdminService.java` + `CommentAdminServiceImpl.java`
- `CommentAdminSummary.java` (DTO record) — id, postId, postTitle, boardCode, boardName, authorUsername, contentPreview, status, createdAt
- `CommentAdminListRequest.java` (요청 파라미터)
- `BbsCommentMapper.java` — `listForAdmin(...)`, `updateStatus(...)`, `adminDeleteComment(...)` 메서드 추가
- `BbsCommentMapper.xml` — 대응 SQL 추가

**MyBatis 매퍼 쿼리 패턴**:
```sql
-- listForAdmin: bbs_comment JOIN bbs_post JOIN bbs_master WHERE 필터 조건
-- updateStatus: UPDATE bbs_comment SET status=#{status}, updated_at=NOW() WHERE id=#{id}
-- adminDeleteComment: UPDATE bbs_comment SET status='DELETED', deleted_at=NOW() WHERE id=#{id}
```

### Frontend

**새로 생성할 파일**:
- `frontend/admin/src/views/board/CommentManagementView.vue`
- i18n 키 추가: `frontend/admin/src/i18n/locales/ko.json` (`board.comments.*`)

**라우트 추가** (`frontend/admin/src/router/index.ts`):
```ts
{ path: '/board/comments', component: CommentManagementView, meta: { requiresAuth: true } }
```

### 데이터베이스

새 마이그레이션 불필요. 기존 `bbs_comment.status` 컬럼 활용.

## 5. 범위 외 (Out of Scope)

- 댓글 일괄(bulk) 처리 (이후 SPEC으로 분리)
- 댓글 작성자 계정 정지 연동
- 스팸 자동 감지
- 댓글 신고 기능
