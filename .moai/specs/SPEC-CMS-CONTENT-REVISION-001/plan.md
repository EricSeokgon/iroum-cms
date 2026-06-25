# SPEC-CMS-CONTENT-REVISION-001 — 구현 계획 (plan.md)

> 우선순위 라벨(High/Medium/Low)과 단계 순서로 표기. 시간 추정 없음.

## 1. 기술 접근 (Technical Approach)

### 1.1 도메인 분리 원칙 [HARD]

게시물(`domain.board`)과 페이지(`domain.content.page`)는 **분리된 채로** 각자 diff·롤백·낙관적 잠금을 보강한다. 단일 통합 RevisionService/테이블은 만들지 않는다 — 두 이력 스키마(개별 컬럼 vs JSONB 스냅샷)가 근본적으로 다르므로 공통 추상화는 비용 대비 가치가 없다(over-engineering 회피).

공유는 **표현 계층(프론트 컴포넌트)** 과 **diff 계산 유틸**에 한한다.

### 1.2 낙관적 잠금 (REQ-REV-005)

- **게시물**: `bbs_post`에 `version INT NOT NULL DEFAULT 1` 추가(V54). 수정 요청 DTO에 `expectedVersion` 추가. UPDATE를 `... SET ..., version = version + 1 WHERE id = ? AND version = ?` 로 변경. MyBatis update 반환 행 수 == 0 → `OptimisticLockConflictException` → `409`.
- **페이지**: 스키마 변경 없음. `updatePage`의 UPDATE에 `WHERE id=? AND current_version=?` 추가, `current_version = current_version + 1`. 요청 DTO `PageUpdateRequest`에 `expectedVersion` 추가.
- 충돌 응답 바디: `{ code: "REVISION_CONFLICT", currentVersion, message }`. 전역 예외 핸들러에 매핑.

### 1.3 Diff 계산 (REQ-REV-003)

- **위치**: 백엔드(서버 단일 진실, 프론트 라이브러리 의존 회피). 후보 라이브러리 `java-diff-utils`(io.github.java-diff-utils) — 미설치이므로 build.gradle 의존성 추가. 또는 단순 LCS 자체 구현(의존성 0). plan 시점 권장: **`java-diff-utils` 추가**(검증된 LCS, 유지보수 부담↓).
- **게시물**: `bbs_post_history`에서 from/to 두 version을 fetch → `title`, `content_html` 각각 라인 분할 후 diff.
- **페이지**: `page_history.snapshot`(JSONB)에서 표시 대상(title, slug, content_block 텍스트)을 평탄화하는 `PageSnapshotFlattener` 신규 유틸 → 필드별 라인 diff. JSONB 파싱은 기존 Jackson 사용.
- **공통 DTO**: `RevisionDiffResponse{ field, fromVersion, toVersion, List<DiffLine> }`, `DiffLine{ type(EQUAL|INSERT|DELETE), oldLineNo, newLineNo, text }`.

### 1.4 롤백 (REQ-REV-004)

- **페이지**: 기존 `PageService.rollbackPage(id, version, userId)` 재사용 — 코드 변경 없음(이미 새 revision 적재 + status DRAFT 강제). diff/낙관락과 무관하게 동작 유지.
- **게시물**: 신규. `PostHistoryService.rollback(postId, version, userId)`:
  1. `bbs_post_history`에서 (postId, version) 스냅샷 fetch (없으면 404 — 기존 `PostHistoryVersionNotFoundException`).
  2. 현재 `bbs_post` 본문을 새 history version으로 적재(`insert` + `nextVersionByPostId` 재사용) — 롤백 전 상태 보존.
  3. `bbs_post` title/content_html을 스냅샷으로 UPDATE, `version + 1`.
  4. 컨트롤러 `POST /api/v1/board/posts/{postId}/rollback/{version}`, RBAC 신규 권한 `POST:ROLLBACK`(또는 기존 인증 패턴 — plan 결정: 권한 세분화 위해 `POST:ROLLBACK` 신규).

### 1.5 Retention (REQ-REV-006)

- `system_setting` 키 `content.revision.maxPerEntity`(기본 50) read.
- 적재 직후(REQ-REV-001 경로) `countByPostId`/페이지 count > N 이면 가장 오래된 version부터 `DELETE WHERE version < (현재 - N + 1)` 또는 ORDER BY version ASC LIMIT (count-N).
- best-effort: 정리 트랜잭션 분리 또는 try/catch로 적재 실패와 격리.

### 1.6 프론트엔드 (REQ-REV-007)

- 공유 컴포넌트(`frontend/admin/src/components/revision/`): `RevisionPanel.vue`(이력 목록 + 두 항목 선택), `DiffViewer.vue`(라인별 색상 표기), `useRevision.ts` 컴포저블.
- 게시물 어댑터: `api/board.ts`에 `getRevisionDiff`, `rollbackPost`, `updatePost(expectedVersion)` 추가. `PostDetailView.vue`/`PostFormView.vue`에 마운트.
- 페이지 어댑터: `api/content.ts`에 `getPageRevisionDiff`, `updatePage(expectedVersion)` 추가(롤백은 기존). `PageEditorView.vue`에 마운트.
- 409 처리: 저장 실패 시 충돌 모달 — 서버 최신 version 안내 + diff 보기 유도.

## 2. 마일스톤 (우선순위 기반)

### M1 (Priority High) — 낙관적 잠금 + 마이그레이션
- V54: `bbs_post.version` 추가 + `system_setting` retention 기본값 INSERT.
- 게시물·페이지 update 경로에 낙관락(409) 적용, `expectedVersion` DTO 필드.
- 전역 예외 → 409 매핑.
- 산출물: 마이그레이션, 게시물/페이지 update 서비스·매퍼·컨트롤러 수정, 충돌 예외.

### M2 (Priority High) — Diff API
- `java-diff-utils` 의존성 추가, `RevisionDiffResponse`/`DiffLine` DTO.
- 게시물 diff: 두 version fetch + title/content diff + `GET /api/v1/board/posts/{postId}/history/diff?from&to`.
- 페이지 diff: `PageSnapshotFlattener` + `GET /api/v1/content/pages/{id}/history/diff?from&to`.

### M3 (Priority Medium) — 게시물 롤백 + Retention
- 게시물 롤백 서비스·컨트롤러(`POST:ROLLBACK`), 롤백=새 revision 적재.
- retention 정리 로직을 게시물·페이지 적재 경로에 통합(best-effort).

### M4 (Priority Medium) — 프론트 통합 UI
- 공유 `RevisionPanel`/`DiffViewer`/`useRevision`.
- 게시물·페이지 화면 마운트, 409 충돌 모달, ko/en i18n(`content.revision.*`).

### M5 (Priority Low) — 검증·문서
- 통합 테스트(낙관락 동시성, diff 정확성, 롤백 불변성, retention 경계).
- API 문서/CHANGELOG.

## 3. 영향 받는 파일 (개요)

백엔드:
- `db/migration/V54__bbs_post_optimistic_lock.sql` (신규)
- `domain/board/...` PostService/PostHistoryService(롤백·diff), BbsPostHistoryMapper(diff fetch), PostController(롤백/diff 엔드포인트), 낙관락 예외
- `domain/content/page/...` PageService(낙관락), PageController(diff 엔드포인트), `PageSnapshotFlattener`(신규)
- 공통 diff 유틸 + `RevisionDiffResponse`/`DiffLine` DTO, 전역 예외 핸들러
- `build.gradle`(java-diff-utils)

프론트:
- `frontend/admin/src/components/revision/RevisionPanel.vue`, `DiffViewer.vue`, `useRevision.ts` (신규)
- `frontend/admin/src/api/board.ts`, `content.ts` (확장)
- `PostDetailView.vue`, `PostFormView.vue`, `PageEditorView.vue` (마운트)
- i18n ko/en `content.revision.*`

## 4. 리스크

| 리스크 | 영향 | 완화 |
|--------|------|------|
| `bbs_post.version`과 `bbs_post_history.version` 의미 혼동 | 적재 시 버전 어긋남 | 둘을 동기화(저장 시 양쪽 +1) 또는 명확히 분리 — M1에서 단일 규칙 확정. 메모리에 기록 |
| 페이지 JSONB snapshot 평탄화의 비결정성(블록 순서/키 순서) | diff 노이즈 | `sort_order` 고정 + 키 정렬 직렬화로 안정화 |
| 미머지 브랜치가 V54+ 점유 | 마이그레이션 충돌 | **run 직전 main 최신 재확인 후 재번호** |
| 기존 `updatePage` 적재 로직 회귀 | 페이지 이력 깨짐 | 낙관락은 WHERE 조건·version 증가만 추가, 적재 본체 불변. 특성화 테스트 선행 |
| `java-diff-utils` 신규 의존성 거부감 | 빌드 정책 | 자체 LCS 구현 대안 보유(의존성 0). 백엔드 expert와 M2 착수 시 확정 |

## 5. 전문가 협의 권장

- **expert-backend**: 낙관적 잠금 트랜잭션 경계, MyBatis 동시성 UPDATE 반환 행 검증, JSONB snapshot 평탄화 설계.
- **expert-frontend**: 공유 Revision 컴포넌트 구조, 409 충돌 UX, diff 뷰어 렌더링.
