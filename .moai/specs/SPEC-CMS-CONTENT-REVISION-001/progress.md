# SPEC-CMS-CONTENT-REVISION-001 — 진행 현황 (progress.md)

> Run Phase 시작: 2026-06-29
> Run Phase 완료: 2026-06-29
> 구현 커밋: ce1f3a8
> 개발 모드: TDD (RED-GREEN-REFACTOR)
> 하네스: standard
> 실행 모드: Solo

## 확정 기술 결정

| # | 결정 | 선택 |
|---|---|---|
| 1 | 낙관락 version 기준 | `bbs_post.version` 단일 기준, history version과 동기화 |
| 2 | `expectedVersion` 누락 정책 | **400** (Bean Validation `@NotNull`) |
| 3 | 페이지 diff 범위 | title/slug만 (content_block은 snapshot에 미저장으로 비범위) |
| 4 | diff 라이브러리 | `io.github.java-diff-utils:java-diff-utils:4.17` |
| 5 | 공유 DTO 위치 | `kr.co.ircp.cms.common.dto`, `kr.co.ircp.cms.common.util` 신규 패키지 |

## 코드베이스 발견 사항

- 페이지 snapshot = title+slug만 (content_block diff 비범위)
- `rollbackPage`는 내용 복원 없음 — DRAFT + 새 revision만 적재
- 페이지 update Java+SQL 이중 version 증가 → 낙관락 적용 시 Java쪽 제거
- 에러 응답: RFC 9457 ProblemDetail + `code` property
- `MigrationOrderIT.EXPECTED_MIGRATION_COUNT=52` → V54 추가 시 53으로 갱신
- 패키지 루트: `kr.co.ircp.cms`

## 마일스톤 진행 현황

- [x] Plan Phase 완료 (spec.md, plan.md, acceptance.md, research.md)
- [x] manager-strategy 실행 계획 수립
- [x] M1: 낙관적 잠금 + 마이그레이션 (Priority High) — 완료 2026-06-29
- [x] M2: Diff API (Priority High) — 완료 2026-06-29
- [x] M3: 게시물 롤백 + Retention (Priority Medium) — 완료 2026-06-29
- [x] M4: 프론트엔드 UI (Priority Medium) — 완료 2026-06-29
- [x] M5: 검증/문서 (Priority Low) — Sync Phase 완료 2026-06-29

## 파일 변경 추적

### M1 대상
- `db/migration/V54__bbs_post_optimistic_lock.sql` (CREATE)
- `MigrationOrderIT.java` (MODIFY: count 52→53, containsExactly에 V54 추가)
- `kr/co/ircp/cms/domain/board/entity/BbsPost.java` (MODIFY: version 필드)
- `kr/co/ircp/cms/domain/board/dto/PostUpdateRequest.java` (MODIFY: expectedVersion)
- `kr/co/ircp/cms/domain/board/mapper/BbsPostMapper.java + .xml` (MODIFY: 낙관락 WHERE)
- `kr/co/ircp/cms/domain/board/service/PostServiceImpl.java` (MODIFY: 낙관락)
- `kr/co/ircp/cms/domain/content/page/dto/PageUpdateRequest.java` (MODIFY: expectedVersion)
- `kr/co/ircp/cms/domain/content/page/mapper/PageMapper.java + .xml` (MODIFY: 낙관락 + 이중증가 제거)
- `kr/co/ircp/cms/domain/content/page/service/PageServiceImpl.java` (MODIFY: 낙관락)
- `kr/co/ircp/cms/exception/RevisionConflictException.java` (CREATE)
- `kr/co/ircp/cms/exception/GlobalExceptionHandler.java` (MODIFY: 409 핸들러)
- Tests: PostServiceOptimisticLockTest, PageServiceOptimisticLockTest, PageUpdateCharacterizationTest, GlobalExceptionHandlerTest, OptimisticLockConcurrencyIT

### M2 대상
- `backend/build.gradle` (MODIFY: java-diff-utils 추가)
- `kr/co/ircp/cms/common/dto/DiffLine.java` (CREATE)
- `kr/co/ircp/cms/common/dto/RevisionDiffResponse.java` (CREATE)
- `kr/co/ircp/cms/common/util/LineDiffCalculator.java` (CREATE)
- `kr/co/ircp/cms/domain/board/service/PostHistoryService.java + Impl` (MODIFY: diff)
- `kr/co/ircp/cms/domain/board/controller/PostController.java` (MODIFY: diff 엔드포인트)
- `kr/co/ircp/cms/domain/content/page/util/PageSnapshotFlattener.java` (CREATE)
- `kr/co/ircp/cms/domain/content/page/service/PageHistoryService.java + Impl` (MODIFY: diff)
- `kr/co/ircp/cms/domain/content/page/controller/PageController.java` (MODIFY: diff 엔드포인트)

### M3 대상
- `kr/co/ircp/cms/domain/board/service/PostHistoryService.java + Impl` (MODIFY: rollback)
- `kr/co/ircp/cms/domain/board/controller/PostController.java` (MODIFY: rollback 엔드포인트)
- `kr/co/ircp/cms/domain/board/mapper/BbsPostHistoryMapper.java + .xml` (MODIFY: deleteOldest)
- `kr/co/ircp/cms/domain/content/page/mapper/PageHistoryMapper.java + .xml` (MODIFY: deleteOldest)
- `kr/co/ircp/cms/common/service/RevisionRetentionService.java` (CREATE)
- `kr/co/ircp/cms/domain/board/service/PostServiceImpl.java` (MODIFY: retention 호출)
- `kr/co/ircp/cms/domain/content/page/service/PageServiceImpl.java` (MODIFY: retention 호출)

### M4 대상
- `frontend/admin/src/components/revision/RevisionPanel.vue` (CREATE)
- `frontend/admin/src/components/revision/DiffViewer.vue` (CREATE)
- `frontend/admin/src/components/revision/ConflictModal.vue` (CREATE)
- `frontend/admin/src/components/revision/useRevision.ts` (CREATE)
- `frontend/admin/src/api/board.ts` (MODIFY: diff/rollback/expectedVersion)
- `frontend/admin/src/api/content.ts` (MODIFY: diff/expectedVersion)
- `frontend/admin/src/views/board/PostDetailView.vue` (MODIFY: RevisionPanel 마운트)
- `frontend/admin/src/views/board/PostFormView.vue` (MODIFY: expectedVersion + ConflictModal)
- `frontend/admin/src/views/content/PageEditorView.vue` (MODIFY: RevisionPanel + ConflictModal)
- i18n ko.json/en.json (MODIFY: content.revision.* 키)
