---
id: SPEC-CMS-POST-HISTORY-001
version: 0.1.0
status: Draft
created: 2026-06-10
updated: 2026-06-10
author: manager-spec
---

# SPEC-CMS-POST-HISTORY-001 — 구현 계획 (plan.md)

## 1. 기술 접근

`bbs_post_history` 테이블에 적재된 게시글 버전 스냅샷을 조회하는 read-only 경로를 백엔드(MyBatis 매퍼 → 서비스 → 컨트롤러)와 관리자 프론트엔드(API 클라이언트 → 히스토리 탭)로 구현한다. 신규 DB 마이그레이션은 없으며, 기존 인덱스 `idx_post_history_post(post_id, version DESC)` 가 페이징·정렬을 그대로 커버한다.

스택: Spring Boot + egovframe + MyBatis (백엔드), Vue 3 + Element Plus (관리자 프론트). 패키지 규약 `kr.co.ircp.cms.domain.board`.

## 2. 마일스톤 (우선순위 기반)

### M1 (Priority High) — 백엔드 read API

- `BbsPostHistoryMapper`에 페이징 목록 조회 메서드 추가:
  - `findSummariesByPostId(postId, offset, limit)` — `bbs_post_history h LEFT JOIN users u ON h.edited_by = u.id`, `ORDER BY h.version DESC`, content_html 제외.
  - `countByPostId(postId)` — 페이징 total.
  - `findByPostIdAndVersion(postId, version)` — 단건 전체 본문(content_html 포함).
- `BbsPostHistoryMapper.xml` 에 대응 resultMap/SQL 추가 (기존 write SQL은 변경 금지).
- DTO 신규: `PostHistorySummary`(version, editorName, editedAt, editReason, title), `PostHistoryDetail`(+ contentHtml).
- 서비스: `PostHistoryService` 신규 또는 `PostService` 확장 — `listHistory(postId, page, size): PageResponse<PostHistorySummary>`, `getHistoryVersion(postId, version): PostHistoryDetail`(미존재 시 예외 → 404).
- 컨트롤러: `PostController`(또는 신규 `PostHistoryController`)에 두 엔드포인트 추가, `@PreAuthorize("isAuthenticated()")`.
- 단위 테스트: 매퍼/서비스/컨트롤러 (페이징 정렬, 404, 인증).

### M2 (Priority High) — 관리자 UI 히스토리 탭

- `frontend/admin/src/api/board.ts` 에 `getPostHistory(postId, page, size)`, `getPostHistoryVersion(postId, version)` 추가.
- `PostDetailView.vue` 에 히스토리 탭(Element Plus `el-tabs` 또는 모달) 추가:
  - 페이징 표: 버전 / 수정자 / 수정 일시 / 수정 사유.
  - 행 선택 → 해당 버전 본문(제목 + content_html) 읽기 전용 표시.
  - 빈 상태 처리(이력 없음).
- read-only 보장: 복원/편집/삭제 컨트롤 미배치.

### M3 (Priority Medium) — 마무리

- ko/en i18n 키 (`board.postHistory.*`).
- 빈 상태·로딩·오류(404/403) UX 처리.
- 프론트 단위 테스트(가능 범위).

## 3. 영향 파일

| 영역 | 파일 | 변경 유형 |
|------|------|-----------|
| 매퍼 IF | `repository/BbsPostHistoryMapper.java` | 메서드 추가 (write 메서드 보존) |
| 매퍼 XML | `mapper/board/BbsPostHistoryMapper.xml` | SQL 추가 (write SQL 보존) |
| DTO | `dto/PostHistorySummary.java`, `dto/PostHistoryDetail.java` | 신규 |
| 서비스 | `service/PostHistoryService(Impl).java` 또는 `PostService` 확장 | 신규/확장 |
| 컨트롤러 | `controller/PostController.java` 또는 `PostHistoryController.java` | 엔드포인트 추가 |
| 프론트 API | `frontend/admin/src/api/board.ts` | 함수 추가 |
| 프론트 뷰 | `frontend/admin/src/views/board/PostDetailView.vue` | 히스토리 탭 추가 |
| i18n | `ko.json`, `en.json` | 키 추가 |

## 4. API 응답 스키마 (초안)

목록 — `GET /api/v1/board/posts/{postId}/history?page=0&size=20`:

```
PageResponse {
  content: [
    { version: int, editorName: string|null, editedAt: ISO-8601, editReason: string|null, title: string }
  ],
  page, size, totalElements, totalPages
}
```

단건 — `GET /api/v1/board/posts/{postId}/history/{version}`:

```
{ version, editorName, editedAt, editReason, title, contentHtml }
```

## 5. 리스크

- **이력 데이터 부재**: 테이블은 있으나 게시글 수정이 발생하지 않은 게시글은 스냅샷이 0건 → 빈 상태 UX 필수(REQ-PH-009). 기능 결함이 아님.
- **삭제 사용자 JOIN**: `edited_by` ON DELETE SET NULL 이므로 LEFT JOIN 필수. INNER JOIN 사용 시 행 누락 위험.
- **content_html 크기**: 목록에서 content_html을 반환하면 페이로드 과대 → 목록/단건 분리로 회피.
- **XSS**: content_html을 관리자 화면에 렌더링 — 적재 시점 HtmlSanitizer 처리 전제. 렌더링 경로에서도 sanitization 일관성 확인 필요.

## 6. @MX 태그 후보

- 신규 컨트롤러/서비스 read 메서드: `@MX:NOTE` (SPEC 연결).
- 매퍼 read SQL: write SQL과 구분되는 `@MX:NOTE` (조회 전용, 페이징/정렬 인덱스 의존).
