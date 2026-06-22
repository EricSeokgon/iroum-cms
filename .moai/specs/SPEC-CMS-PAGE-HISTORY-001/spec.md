---
id: SPEC-CMS-PAGE-HISTORY-001
version: 0.1.1
status: draft
created_at: 2026-06-22
updated_at: 2026-06-22
priority: medium
labels: [cms, page-history, retention, rollback, audit]
author: ircp
issue_number: null
---

# SPEC-CMS-PAGE-HISTORY-001: 페이지 버전 이력 관리 고도화 (Page Version History Enhancement)

## HISTORY

- v0.1.1 (2026-06-22): plan-auditor 감사 반영. 프론트매터 표준화(status=draft, created_at/updated_at, priority=medium, labels), 19개 AC 전체를 EARS "the system shall" 형식으로 재작성, REQ-PHIST-004 감사 액션을 Option A(action="UPDATE", afterValue에 from/to version)로 확정(선택지 제거), AC-PHIST-019 권한 미보유 시 버튼 비렌더(숨김) 명확화.
- v0.1.0 (2026-06-22): 최초 작성. 기구현된 페이지 이력/롤백 기능(SPEC-CMS-004 REQ-CONTENT-005-D-2/6/7)의 미완성 갭 5건을 보강하는 SPEC. 보존 정책(GC), 롤백 IT 완성, changeSummary 자동생성, 롤백 감사로그, 목록 화면 이력 진입점.

## 개요 (Overview)

페이지 버전 이력 및 롤백 기능은 SPEC-CMS-004(콘텐츠/메뉴/사이트 관리)의 일부로 **이미 부분 구현**되어 있다. 본 SPEC은 기존 구현을 재설계하지 않고, **누락된 운영/품질 갭만** 보강한다.

### 기구현 자산 (재사용, 재설계 금지)

| 영역 | 자산 | 상태 |
|------|------|------|
| API | `GET /api/v1/content/pages/{id}/history`, `POST /api/v1/content/pages/{id}/rollback/{version}` | 동작 |
| 서비스 | `PageServiceImpl.getPageHistory()`, `rollbackPage()` | 동작 |
| 엔티티 | `PageHistory` (pageId, version, snapshot jsonb, editedBy, changeSummary, editedAt) | 동작 |
| 매퍼 | `PageHistoryMapper.xml` (findByPageId, findByPageIdAndVersion, insert) | 동작 |
| 프론트 | `PageHistoryDialog.vue`(목록+롤백 popconfirm+JSON Diff), `JsonDiffPanel.vue`, `PageEditorView.vue` History 버튼 | 동작 |
| 테이블 | `page_history` (SPEC-CMS-004 마이그레이션) | 존재 |
| RBAC | `PAGE:HISTORY:READ`, `PAGE:ROLLBACK` 권한 | 존재 |

### 본 SPEC 범위 (5건 갭)

1. **보존 정책(Retention)** — 이력이 무한 증가. 페이지당 최대 N개(기본 50) 유지 + 초과 시 오래된 항목 정리 배치.
2. **롤백 IT 완성** — `AC-PAGE-10` 주석 "실 롤백 로직은 page_history 시드 필요"가 명시하듯, 실제 페이지 내용 복원이 통합 테스트되지 않음. 시드 후 롤백 → 실제 복원 검증 필요.
3. **changeSummary 자동생성** — 현재 프론트에서 입력한 changeSummary를 그대로 사용. diff 기반으로 "title 변경, content 변경" 형태 요약을 자동 생성(사용자 미입력 시 fallback).
4. **롤백 감사로그** — 롤백 수행 시 `audit_log`에 기록 안 됨. 롤백을 감사 추적 대상에 포함.
5. **목록 화면 이력 진입점** — 현재 에디터에서만 이력 접근 가능. 목록 화면(`PageListView`)에서도 빠른 "이력" 액션 버튼 제공.

---

## EARS 요구사항 (Requirements)

### REQ-PHIST-001: 이력 보존 정책 및 정리 배치 (Retention)

**EARS (State-Driven + Event-Driven):**
- WHILE 한 페이지의 `page_history` 항목 수가 보존 한도(`page.history.retention.max-versions`, 기본 50)를 초과하는 동안, the system SHALL 가장 오래된(version ASC) 초과분을 정리 대상으로 식별한다.
- WHEN 이력 정리 배치(`PageHistoryRetentionJob`)가 실행되면, THEN the system SHALL 페이지별로 최신 N개 version을 보존하고 나머지를 DELETE 한다.
- IF 보존 한도가 설정되지 않았거나 0 이하이면, THEN the system SHALL 정리를 수행하지 않고(무한 보존) 경고 로그만 남긴다.

**제약:**
- 롤백의 기준점이 되는 version은 보존 윈도 내에 있어야 한다. 정리는 항상 최신 version부터 N개를 보존하므로, 가장 최근에 생성된 항목은 절대 삭제되지 않는다.
- 정리 대상은 해당 페이지 단위로만 평가한다(전역 카운트가 아님).
- `page_history`의 다른 SPEC(SPEC-CMS-004) 동작에는 영향이 없어야 한다.

**Acceptance Criteria:**
- **AC-PHIST-001**: When the retention batch job runs for a page that has 52 history entries (version 1–52) and max-versions=50, the system SHALL retain exactly 50 entries (version 3–52) and delete entries for version 1 and 2.
- **AC-PHIST-002**: While the retention batch job completes execution, the system SHALL preserve the entry corresponding to the page's currentVersion regardless of retention window boundaries.
- **AC-PHIST-003**: When max-versions is configured as 0 or not configured, the system SHALL execute the retention batch without deleting any history entries and emit a warning log.
- **AC-PHIST-004**: When the retention batch job runs for page A (52 entries) and page B (10 entries), the system SHALL delete only page A's overflow entries and leave page B's 10 entries unchanged.

**기술 접근:**
- 신규: `backend/.../page/job/PageHistoryRetentionJob.java` (`@Scheduled` 또는 수동 트리거 가능한 컴포넌트).
- 매퍼 추가: `PageHistoryMapper.xml` — `countByPageId`, `deleteOldestByPageId(pageId, keepCount)` (서브쿼리로 보존 대상 version 제외 후 DELETE).
- 설정: `application.yml` `page.history.retention.max-versions: 50`.
- 마이그레이션: `page_history(page_id, version)` 복합 인덱스가 없으면 **V56**에서 추가(정리 쿼리 성능). 기존 인덱스 확인 후 결정.

---

### REQ-PHIST-002: 롤백 통합 테스트 완성 (실제 복원 검증)

**EARS (Event-Driven):**
- WHEN 관리자가 시드된 이전 version으로 롤백을 요청하면, THEN the system SHALL 해당 version의 snapshot을 페이지 필드(title, slug 등)로 실제 복원하고 새 version으로 이력에 기록한다.
- WHEN 롤백이 성공하면, THEN the system SHALL 페이지 status를 `DRAFT`로 강제하고 `currentVersion`을 1 증가시킨다.

**제약:**
- 본 요구사항은 **기존 동작의 검증 강화**가 주목적이나, `PageServiceImpl.rollbackPage()`(line 224)가 현재 snapshot JSON을 파싱하지 않고 status만 DRAFT로 강제하는 한계가 있음. 통합 테스트가 "실제 내용 복원"을 검증하려면 서비스의 snapshot 파싱·필드 복원 로직 보완이 선행되어야 한다. (이 구현 보완을 REQ-PHIST-002 범위에 포함한다.)
- snapshot 포맷은 현재 `{"title":"...","slug":"..."}` 형태(line 105). 복원 시 동일 키를 파싱한다. 누락 키는 기존 값 유지.

**Acceptance Criteria:**
- **AC-PHIST-005**: When an administrator rolls back page P to version 1 (seeded with title='원본', slug='orig') via POST /rollback/1, the system SHALL restore the page so that title equals '원본' and slug equals 'orig'.
- **AC-PHIST-006**: When rollback to version 1 completes, the system SHALL set the page status to DRAFT and increment currentVersion to rollback-prior-version + 1.
- **AC-PHIST-007**: When rollback to version 1 completes, the system SHALL insert a new page_history entry whose changeSummary contains the pattern 'ROLLBACK_FROM_v1'.
- **AC-PHIST-008**: When a rollback request targets a version that does not exist in page_history, the system SHALL return a 4xx error response and leave the page content unchanged.

**기술 접근:**
- 수정: `PageServiceImpl.rollbackPage()` — snapshot JSON 파싱(Jackson `ObjectMapper`) 후 `title`/`slug` 등 필드 복원. line 224~228 보완.
- 수정/추가: `backend/src/test/java/.../content/PageIT.java` §E-10 — `insertPageHistory(pageId, version, snapshotJson)` 헬퍼 추가, AC-PAGE-10 주석 한계 해소.
- 기존 AC-PAGE-10(권한 게이트 검증)은 유지하고, 실제 복원 검증 테스트를 별도 케이스로 추가한다.

---

### REQ-PHIST-003: changeSummary 자동 생성 (diff 기반, 사용자 입력 fallback)

**EARS (Event-Driven + Unwanted):**
- WHEN 사용자가 페이지를 수정하면서 `changeSummary`를 제공하지 않으면(null/blank), THEN the system SHALL 직전 snapshot과 신규 값의 차이를 기반으로 간단한 요약(예: "제목 변경, 슬러그 변경")을 자동 생성하여 이력에 기록한다.
- WHEN 사용자가 `changeSummary`를 명시적으로 제공하면, THEN the system SHALL 사용자 입력을 우선 사용한다(자동 생성을 덮어쓰지 않는다).
- IF 변경된 필드가 없으면(동일 값 저장), THEN the system SHALL "변경 없음" 또는 빈 요약 대신 사전 정의된 기본 문구를 기록한다.

**제약:**
- diff 비교 대상 필드는 현재 snapshot에 포함된 필드(title, slug)로 한정한다. 향후 content_block 확장은 본 SPEC 범위 밖(Exclusions 참조).
- 자동 요약은 사람이 읽을 수 있는 짧은 한국어 문구이며 길이 상한(예: 200자)을 둔다.

**Acceptance Criteria:**
- **AC-PHIST-009**: When a page update request omits changeSummary and changes only the title field, the system SHALL auto-generate a changeSummary containing '제목 변경' and record it in the new page_history entry.
- **AC-PHIST-010**: When a page update request omits changeSummary and changes both title and slug, the system SHALL auto-generate a changeSummary containing both '제목 변경' and '슬러그 변경'.
- **AC-PHIST-011**: When a page update request provides changeSummary='긴급 오타 수정', the system SHALL record exactly '긴급 오타 수정' in the history entry without auto-generation override.
- **AC-PHIST-012**: When a page update request omits changeSummary and the submitted values are identical to the current page content, the system SHALL record a non-empty default changeSummary (e.g., '변경 없음') in the history entry.

**기술 접근:**
- 수정: `PageServiceImpl.updatePage()` line 102~110 — history 빌드 전, `changeSummary`가 blank이면 `PageChangeSummaryGenerator.summarize(existing, request)` 호출.
- 신규: `backend/.../page/service/PageChangeSummaryGenerator.java` (순수 함수형 유틸, 필드 비교 → 한국어 라벨 매핑).
- 단위 테스트: `PageChangeSummaryGeneratorTest.java` (필드별 변경 조합 검증).

---

### REQ-PHIST-004: 롤백 작업 감사 로그 (Audit Log)

**EARS (Event-Driven):**
- WHEN 관리자가 페이지 롤백을 성공적으로 수행하면, THEN the system SHALL `audit_log`에 행위(롤백), 대상 페이지 ID, from/to version 정보를 포함한 감사 항목을 기록한다.
- IF 롤백이 실패하면(version 미존재 등), THEN the system SHALL 감사 로그에 result=FAILURE로 기록하거나(또는 AOP의 실패 경로) 기존 예외 흐름을 유지한다.

**제약 (확정된 설계 결정):**
- **채택 결정: Option A를 채택한다.** 롤백 감사로그는 `action="UPDATE"`로 기록하며, from_version과 to_version은 `afterValue` JSON 필드에 저장한다. 마이그레이션은 불필요하다.
- (참고: 기존 `audit_log.action` CHECK 제약은 `CREATE/READ/UPDATE/DELETE/LOGIN/.../EXPORT/BATCH`만 허용하며 `PAGE_ROLLBACK`은 허용 코드가 아니다. Option A는 기존 `UPDATE` 코드를 사용하므로 CHECK 제약을 변경하지 않는다. 이 제약 정보는 맥락 참고용이며, 별도 액션 코드 추가는 본 SPEC 범위 밖이다.)
- 구현: 기존 `@AuditLog(action="UPDATE", entityType="Page")`를 `rollbackPage()`에 적용하고, from/to version 등 상세는 `captureArgs`/`afterValue`(JSON)로 표현한다.
- entityType="Page", entityId=pageId 문자열, 상세(from_version, to_version)는 `afterValue` JSON에 `{"from_version": N, "to_version": M}` 형태로 기록.

**Acceptance Criteria:**
- **AC-PHIST-013**: When a rollback completes successfully, the system SHALL insert one audit_log entry with entityType='Page', entityId equal to the rolled-back pageId, and action='UPDATE'.
- **AC-PHIST-014**: When the audit_log entry for a rollback is created, the system SHALL include from_version and to_version values in the afterValue JSON field of that entry.
- **AC-PHIST-015**: When a rollback request fails due to the target version not existing, the system SHALL not write any entry to audit_log and the existing audit_log state SHALL remain unchanged.

**기술 접근:**
- 수정: `PageServiceImpl.rollbackPage()` 또는 `PageController.rollbackPage()`에 `@AuditLog(action="UPDATE", entityType="Page", captureArgs=true)` 적용(Option A 채택).
- from_version, to_version은 `afterValue` JSON에 기록한다(CHECK 제약 변경 없음).
- 통합 테스트: 롤백 후 `audit_log` 조회로 항목 존재·상세 검증(`PageIT` 또는 audit 도메인 IT).

---

### REQ-PHIST-005: 목록 화면 이력 진입점 (PageListView History Button)

**EARS (Event-Driven + Optional):**
- WHERE 사용자가 `PAGE:HISTORY:READ` 권한을 보유한 경우, the system SHALL 페이지 목록(`PageListView`) 각 행의 액션 영역에 "이력" 버튼을 노출한다.
- WHEN 사용자가 목록에서 "이력" 버튼을 클릭하면, THEN the system SHALL 해당 페이지의 `PageHistoryDialog`를 열어 에디터에서와 동일한 이력 목록·비교·롤백 기능을 제공한다.
- WHEN 목록에서 롤백이 수행되면, THEN the system SHALL 목록을 새로고침하여 변경된 status/version을 반영한다.

**제약:**
- 기존 `PageHistoryDialog.vue`를 재사용한다(신규 다이얼로그 생성 금지).
- 목록 액션 열 폭(현재 width="230") 초과 시 레이아웃 조정(폭 확대 또는 드롭다운). 기존 버튼(수정/발행/예약/철회)을 제거하지 않는다.
- 권한 없음(`PAGE:HISTORY:READ` 미보유) → "이력" 버튼은 렌더링하지 않는다(숨김 처리, disabled 아님).

**Acceptance Criteria:**
- **AC-PHIST-016**: When the PageListView is rendered, the system SHALL display a history action button for each page row in the actions column.
- **AC-PHIST-017**: When a user clicks the history button on a page row in PageListView, the system SHALL open PageHistoryDialog with that page's ID and load the history list.
- **AC-PHIST-018**: When a rollback is performed from the PageHistoryDialog opened via PageListView and the rolledBack event fires, the system SHALL refresh the page list to reflect the updated status (DRAFT) and incremented version.
- **AC-PHIST-019**: Where a user does not hold the PAGE:HISTORY:READ permission, the system SHALL not render the history button in the PageListView action column.

**기술 접근:**
- 수정: `frontend/admin/src/views/content/PageListView.vue` — 액션 열에 "이력" 버튼 추가, `PageHistoryDialog` 임포트, `historyOpen`/`historyPageId` 상태, rolledBack 핸들러로 `loadPages()` 호출.
- 재사용: `PageHistoryDialog.vue`(수정 불필요 또는 최소). i18n 키 `content.page.history.*` 기존 키 재사용.
- 권한 가드: 프론트 권한 스토어의 `PAGE:HISTORY:READ` 평가(기존 RBAC 가드 패턴 따름).

---

## Acceptance Criteria (Summary)

All acceptance criteria are enumerated within each REQ section above (AC-PHIST-001 through AC-PHIST-019). The table below provides a consolidated index for traceability.

| AC | 요구사항 | 핵심 검증 포인트 |
|----|----------|-----------------|
| AC-PHIST-001 | REQ-PHIST-001 | 정리 배치 실행 후 version 3~52 (50개) 보존 |
| AC-PHIST-002 | REQ-PHIST-001 | currentVersion 항목은 정리 대상 제외 |
| AC-PHIST-003 | REQ-PHIST-001 | max-versions=0 시 삭제 없음 |
| AC-PHIST-004 | REQ-PHIST-001 | 페이지 간 정리 격리 |
| AC-PHIST-005 | REQ-PHIST-002 | 롤백 후 title/slug 실제 복원 검증 |
| AC-PHIST-006 | REQ-PHIST-002 | 롤백 후 status=DRAFT, version 증가 |
| AC-PHIST-007 | REQ-PHIST-002 | changeSummary에 ROLLBACK_FROM_v1 패턴 포함 |
| AC-PHIST-008 | REQ-PHIST-002 | 존재하지 않는 version 롤백 시 4xx + 페이지 불변 |
| AC-PHIST-009 | REQ-PHIST-003 | title만 변경 시 "제목 변경" 자동 생성 |
| AC-PHIST-010 | REQ-PHIST-003 | title+slug 변경 시 복합 요약 자동 생성 |
| AC-PHIST-011 | REQ-PHIST-003 | 명시적 changeSummary 입력 시 자동 생성 미적용 |
| AC-PHIST-012 | REQ-PHIST-003 | 변경 없음 저장 시 기본 문구 기록 |
| AC-PHIST-013 | REQ-PHIST-004 | 롤백 성공 시 audit_log 1건 (action=UPDATE) |
| AC-PHIST-014 | REQ-PHIST-004 | audit_log afterValue에 from/to version 포함 |
| AC-PHIST-015 | REQ-PHIST-004 | 롤백 실패 시 audit_log 기록 없음 |
| AC-PHIST-016 | REQ-PHIST-005 | PageListView 각 행에 "이력" 버튼 표시 |
| AC-PHIST-017 | REQ-PHIST-005 | "이력" 클릭 시 PageHistoryDialog 오픈 |
| AC-PHIST-018 | REQ-PHIST-005 | 롤백 후 목록 자동 새로고침 |
| AC-PHIST-019 | REQ-PHIST-005 | PAGE:HISTORY:READ 권한 없음 시 버튼 미렌더 |

---

## Exclusions (What NOT to Build)

- [제외] 기존 이력/롤백 API·서비스·엔티티·매퍼·프론트 다이얼로그의 재설계 — 동작 중이므로 보강만 한다.
- [제외] content_block / i18n_resource 전체 풀-스냅샷 복원 — 현재 snapshot은 title/slug 한정. 전체 스냅샷 확장은 별도 SPEC.
- [제외] changeSummary의 다국어 자동 번역 — 한국어 요약만 생성.
- [제외] 이력 항목의 영구 보관(archive to cold storage) — 본 SPEC은 단순 DELETE 정리만. 아카이빙 정책은 거버넌스(governance) 도메인 별도 SPEC.
- [제외] 롤백 시 사용자 확인 워크플로우/승인 게이트 — 기존 popconfirm 유지.
- [제외] 시민(public) 화면의 페이지 버전 노출 — 관리자 전용 기능.
- [제외] 게시판 게시물 이력(SPEC-CMS-POST-HISTORY-001) 통합 — 유사 패턴이나 별개 도메인.

---

## 관련 SPEC (Related)

- **부모**: SPEC-CMS-004 (콘텐츠/메뉴/사이트 관리) — REQ-CONTENT-005-D-2/6/7 (이력/롤백 기구현)
- **유사 패턴**: SPEC-CMS-POST-HISTORY-001 (게시판 게시물 이력 뷰어)
- **감사 인프라**: SPEC-CMS-005 (audit_log, `@AuditLog` AOP)

## 마이그레이션 주의

작성 시점 최신 마이그레이션은 **V55**(SPEC-CMS-REVIEW-001 draft 표기). 본 SPEC이 인덱스 또는 CHECK 제약 변경을 필요로 하면 **V56**을 사용한다. run 직전 실제 최신 마이그레이션 번호를 재확인하고 충돌 시 재번호한다. 단, REQ-PHIST-001(인덱스)·REQ-PHIST-004 채택안 A는 마이그레이션 불필요할 수 있으므로 plan 단계에서 확정한다.
