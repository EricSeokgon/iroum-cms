# SPEC-CMS-CONTENT-REVISION-001 — 리서치: 콘텐츠 수정 이력(Revision) 관리

> 본 문서는 SPEC 작성 전 코드베이스 심층 조사 결과다. **프롬프트의 전제 일부가 실제 코드와 달라 정정한다.**

## 0. 프롬프트 전제 vs 실제 (정정)

| 프롬프트 전제 | 실제 코드베이스 | 영향 |
|---|---|---|
| `SPEC-CMS-PAGE-HISTORY-001`이 페이지 버전이력+기본 롤백을 구현 | **그런 SPEC은 존재하지 않음.** 페이지 이력/롤백은 `REQ-CONTENT-005-D`(콘텐츠 스키마)의 일부로 `content.page` 도메인에 직접 구현되어 있음 | SPEC은 "PAGE-HISTORY-001 참조" 대신 기존 `PageService.rollbackPage` 재사용을 명시 |
| 게시물(Post)에는 버전/이력 테이블이 아직 없을 수 있음 | **`bbs_post_history`(V10) 존재.** `SPEC-CMS-POST-HISTORY-001`(Completed)이 read-only 뷰어 구현(목록/단건). diff·롤백·충돌감지는 **명시적 제외** | 게시물 측 갭 = 롤백 + diff + 충돌감지 (이력 적재·조회는 이미 존재) |
| `PageChangeSummaryGenerator` 같은 diff 로직이 존재 | **존재하지 않음.** 백엔드/프론트 어디에도 diff 라이브러리·컴포넌트 없음 | diff는 두 도메인 모두에 대해 신규 |

결론: 본 SPEC은 신규 도메인을 만드는 것이 아니라, **이미 분리되어 존재하는 두 콘텐츠 도메인(게시물·페이지)의 이력 인프라 위에** 공통 갭(diff·통합 롤백·진짜 낙관적 잠금·retention·통합 UI)을 얹는 작업이다.

---

## 1. 게시물 도메인 — `bbs_post` / `bbs_post_history` (V10)

### 1.1 스키마 (`V10__board_schema.sql`)

```sql
CREATE TABLE bbs_post (
    id BIGINT ... PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    content_html TEXT NOT NULL,
    content_text TEXT NOT NULL,
    ...
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',  -- DRAFT/PUBLISHED/HIDDEN/DELETED
    created_at, updated_at, deleted_at
    -- ⚠️ version 컬럼 없음 (낙관적 잠금 불가) / slug 컬럼 없음
);

CREATE TABLE bbs_post_history (
    id BIGINT ... PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES bbs_post(id) ON DELETE CASCADE,
    version INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    content_html TEXT NOT NULL,        -- 개별 컬럼 스냅샷 (JSONB 아님)
    edited_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    edit_reason VARCHAR(200),
    edited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, version)
);
CREATE INDEX idx_post_history_post ON bbs_post_history(post_id, version DESC);
```

핵심: **`bbs_post`에 version 컬럼이 없다.** 따라서 게시물 낙관적 잠금은 새 컬럼 추가가 필요하다(유일한 마이그레이션 사유). 게시물은 slug가 없으므로 REQ의 "slug diff"는 게시물에 적용되지 않는다(페이지 전용).

### 1.2 기존 백엔드 (도메인 `board`)
- `BbsPostHistory` 엔티티, `BbsPostHistoryMapper`
  - write: `insert`, `nextVersionByPostId` (게시물 수정 시 적재) — **변경 금지**
  - read: `findPageByPostId(offset,size)`, `countByPostId`, `findByPostIdAndVersion` (POST-HISTORY-001에서 추가)
- `PostHistoryService` / `PostHistoryServiceImpl`: 목록 페이징 + 단건 버전 조회 (read-only)
- DTO: `PostHistoryItem`(목록, content_html 제외), `PostHistoryDetail`(단건, 전체 본문)
- 예외: `PostHistoryVersionNotFoundException` (404 매핑)
- 경로: `GET /api/v1/board/posts/{postId}/history`, `/history/{version}`
- RBAC: PostController `@PreAuthorize("isAuthenticated()")`

### 1.3 게시물 측 갭
- **롤백 없음** (페이지에는 있으나 게시물에는 없음)
- **diff 없음** (POST-HISTORY-001이 명시적으로 제외)
- **낙관적 잠금 없음** (`bbs_post`에 version 컬럼 부재)
- **retention 없음**

---

## 2. 페이지 도메인 — `page` / `page_history` (V13)

### 2.1 스키마 (`V13__content_schema.sql`)

```sql
CREATE TABLE page (
    id BIGINT ... PRIMARY KEY,
    site_id, template_id, menu_id,
    code VARCHAR(100), title VARCHAR(300), slug VARCHAR(255),
    status VARCHAR(20) DEFAULT 'DRAFT',  -- DRAFT/SCHEDULED/PUBLISHED/RETRACTED
    ...
    current_version INT NOT NULL DEFAULT 1,   -- ⚠️ 카운터(수정 시 +1). UPDATE WHERE에 미사용 → 진짜 낙관적 잠금 아님
    created_by, updated_by,
    created_at, updated_at, deleted_at
);

-- 콘텐츠는 별도 테이블 (JSONB 블록 배열)
CREATE TABLE content_block (
    page_id, block_type, sort_order, payload JSONB, version INT, ...
);

CREATE TABLE page_history (
    id, page_id, version INT,
    snapshot JSONB NOT NULL,     -- page row + content_block 배열 + i18n 통째
    edited_by BIGINT NOT NULL,
    edited_at, change_summary VARCHAR(500),
    UNIQUE (page_id, version)
);
CREATE INDEX idx_page_history_page ON page_history(page_id, version DESC);
```

### 2.2 기존 백엔드 (도메인 `content.page`) — **이미 풍부함**
- `PageService` 인터페이스 메서드(발췌):
  - `updatePage(id, request, updatedBy)` — 이력 누적 + slug 변경 시 seo_redirect 자동 INSERT
  - `getPageHistory(id)` — **이력 목록 조회 (version DESC) 이미 존재**
  - `rollbackPage(id, version, rolledBackBy)` — **특정 버전 롤백 (status=DRAFT 강제) 이미 존재**
- `PageController` 엔드포인트 이미 존재:
  - `GET /api/v1/content/pages/{id}/history` — `@PreAuthorize("hasAuthority('PAGE:HISTORY:READ')")`
  - `POST /api/v1/content/pages/{id}/rollback/{version}` — `@PreAuthorize("hasAuthority('PAGE:ROLLBACK')")`
- `PageHistoryService`, `PageHistoryServiceImpl`, `PageHistoryMapper`, `PageHistory` 엔티티
- DTO `PageHistoryResponse`: id, pageId, version, snapshot(String), editedBy, editedAt, changeSummary
- RBAC authorities 이미 정의: `PAGE:READ`, `PAGE:WRITE`, `PAGE:PUBLISH`, `PAGE:HISTORY:READ`, `PAGE:ROLLBACK`

### 2.3 페이지 측 갭
- **diff 없음** (스냅샷 전체 표시만, 두 버전 비교 시각화 없음)
- **진짜 낙관적 잠금 409 없음** (`current_version`은 단순 카운터, `updatePage`가 WHERE에서 검증하지 않음 → lost update 가능)
- **retention 없음**

---

## 3. 공통 인프라 (재사용 가능)

| 항목 | 위치 | 재사용 방식 |
|------|------|------------|
| `PageResponse<T>` 페이징 DTO | `domain.auth.dto` | 게시물 이력 목록 페이징에 이미 사용 중 |
| `system_setting` 테이블 (V14) | key/value/value_type(STRING/INT/BOOL/JSON)/description | **retention 정책값(max N) 저장에 재사용** — 신규 테이블 불필요 |
| `PageService.rollbackPage` | `content.page` | 페이지 롤백은 그대로 재사용. 게시물 롤백은 동일 패턴 신규 구현 |
| RBAC authority 패턴 | `PAGE:*` / PostController `isAuthenticated()` | 신규 권한 `POST:ROLLBACK`, diff용 `*:HISTORY:READ` 재사용 |
| `users` LEFT JOIN (editor 표시명) | POST-HISTORY-001에서 확립 | diff/목록에 editor 이름 표시 시 동일 패턴 |

### 3.1 diff 라이브러리 현황 (조사 결과)
- 백엔드 `build.gradle`/`pom.xml`: **diff 라이브러리 없음** (`java-diff-utils` 미설치)
- 프론트 `frontend/admin`: **diff 컴포넌트/라이브러리 없음** (`vue-diff`/`diff2html`/`jsdiff` 미설치)
- → diff는 신규 도입. 백엔드 라인 단위 LCS(예: `java-diff-utils`) 또는 프론트 라이브러리 중 택1 (plan.md 결정 사항)

---

## 4. 프론트엔드 현황

| 화면 | 경로 | 비고 |
|------|------|------|
| 게시물 상세(admin) | `frontend/admin/src/views/board/PostDetailView.vue` | POST-HISTORY-001 히스토리 탭 존재(read-only) |
| 게시물 작성/수정(admin) | `frontend/admin/src/views/board/PostFormView.vue` | 저장 화면 — 충돌감지 통합 지점 |
| 페이지 편집(admin) | `frontend/admin/src/views/content/PageEditorView.vue` | 저장 화면 — 충돌감지 통합 지점 |
| API 클라이언트 | `frontend/admin/src/api/board.ts`, `content.ts` | 각 도메인별 분리 |

**중요**: 게시물과 페이지는 UI/API가 완전히 분리되어 있다. "통합 Revision UI"는 동일 컴포넌트를 **두 화면에 각각 마운트**하는 형태(공유 컴포넌트 + 도메인별 어댑터)로 설계해야 한다.

---

## 5. 마이그레이션 번호

- main 디스크 최신 = **V53** (`V53__kpi_definition_activity_seed.sql`)
- 본 SPEC 신규 마이그레이션 필요 = **1건** → **V54**
  - 내용: `bbs_post`에 낙관적 잠금용 `version INT NOT NULL DEFAULT 1` 컬럼 추가
  - (페이지는 `current_version` 기존 보유 → 마이그레이션 불필요. 단 의미를 카운터→낙관락으로 활용)
  - (retention 설정은 `system_setting`에 row INSERT — 별도 마이그레이션 또는 V54에 포함)
- ⚠️ 메모리 주의: 미머지 브랜치들이 V54~V59를 잠정 점유(AI-004/SURVEY-001=V54, REVIEW-001=V55, POINTS-001=V59 등). **run 직전 main 최신 재확인 후 충돌 시 재번호.**

---

## 6. 핵심 아키텍처 결정 (SPEC/plan으로 이관)

1. **게시물·페이지 도메인 분리 유지.** 공통 추상화(예: 통합 RevisionService 단일 테이블)는 도입하지 않는다 — 두 스키마(개별 컬럼 vs JSONB 스냅샷)가 근본적으로 다르므로 over-engineering. 각 도메인에 diff/롤백/낙관락을 병렬로 추가한다.
2. **낙관적 잠금**: 게시물은 `version` 신규 컬럼 + `UPDATE ... WHERE id=? AND version=?`. 페이지는 기존 `current_version`을 동일 패턴으로 활용. 0행 영향 시 → `409 Conflict` + 서버 최신 버전 반환.
3. **diff**: 백엔드 계산 권장(서버 단일 진실, 프론트 라이브러리 의존 회피). 게시물=title/content_html 라인 diff. 페이지=title/slug/(블록 직렬화 텍스트) 라인 diff. 페이지 snapshot은 JSONB → 표시용 텍스트로 평탄화 후 diff.
4. **롤백**: 페이지=`rollbackPage` 재사용. 게시물=신규(스냅샷 → bbs_post UPDATE + 새 history 적재, version 증가). 롤백도 새 revision을 만든다(불변성 유지, 과거 버전 파괴 금지).
5. **retention**: `system_setting` 키(예: `content.revision.maxPerEntity`)로 N 설정. 적재 후 초과분(가장 오래된 version) 삭제. best-effort(삭제 실패가 저장을 막지 않음).
6. **UI**: 공유 `RevisionPanel` + `DiffViewer` 컴포넌트를 게시물/페이지 편집 화면 양쪽에 마운트.
