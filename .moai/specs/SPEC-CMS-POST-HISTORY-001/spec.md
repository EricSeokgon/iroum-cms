---
id: SPEC-CMS-POST-HISTORY-001
version: 0.1.0
status: Draft
created: 2026-06-10
updated: 2026-06-10
author: manager-spec
priority: P2
issue_number: TBD
related:
  - SPEC-CMS-AUDIT-LOG-VIEW-001 (관리자 읽기 전용 조회 화면 + 페이지네이션 패턴 참고)
  - REQ-BOARD-002-D-4 (수정 직전 본문 보존 — bbs_post_history 적재 원천)
---

# SPEC-CMS-POST-HISTORY-001 — 게시글 버전 히스토리 뷰어

## HISTORY

- 2026-06-10 (v0.1.0): Draft 작성. `bbs_post_history` 테이블(V10)은 이미 존재하고 게시글 수정 시 스냅샷이 적재되고 있으나, 이를 조회할 관리자 UI가 전무한 gap을 정의. 백엔드 목록/단건 조회 API + 관리자 히스토리 탭(읽기 전용). 신규 DB 마이그레이션 없음.

---

## 1. 개요

### 1.1 목적

관리자가 게시글 상세 화면에서 해당 게시글의 **과거 버전 스냅샷(수정 이력)** 을 조회할 수 있는 읽기 전용 뷰어를 제공한다. 게시글 편집 감사(누가·언제·무엇을 바꿨는지 추적), 잘못된 수정 확인, 이전 본문 참조를 목적으로 한다.

### 1.2 배경 (gap)

- `bbs_post_history` 테이블은 **V10 마이그레이션에서 이미 생성**되어 있고, 게시글 수정 시 직전 본문 스냅샷이 적재되고 있다(REQ-BOARD-002-D-4).
- 백엔드에 `BbsPostHistory` 엔티티와 `BbsPostHistoryMapper`(write 경로 — `insert`, `nextVersionByPostId`, 페이지네이션 없는 `findByPostId`)는 존재하나, **조회를 위한 서비스 메서드·컨트롤러 엔드포인트가 없다.**
- 관리자 화면 `PostDetailView.vue`에는 **히스토리 탭이 없어**, 관리자가 적재된 이력 데이터를 전혀 볼 수 없다.
- 본 SPEC은 이 gap — 적재(write)는 되지만 조회(read)할 수 없는 데이터 — 를 메우는 백엔드 read API + 관리자 read-only UI를 정의한다.

### 1.3 범위 요약

3개 영역 산출물: 백엔드 read 전용 API(목록 페이지네이션 + 단건 버전), 관리자 히스토리 탭 UI, 프론트엔드 API 클라이언트. **신규 DB 마이그레이션은 없다** (테이블·인덱스 모두 V10에 존재).

---

## 2. 이미 존재하는 것 (재사용 인프라)

| 구분 | 항목 | 상태 | 비고 |
|------|------|------|------|
| DB | `bbs_post_history` 테이블 | **존재 (V10)** | 컬럼: id, post_id, version, title, content_html, edited_by(FK users, nullable), edit_reason, edited_at |
| DB | `idx_post_history_post (post_id, version DESC)` 인덱스 | **존재 (V10)** | 버전 역순 조회에 최적 — 신규 인덱스 불필요 |
| 도메인 | `BbsPostHistory` 엔티티 | **존재** | id, postId, version, title, contentHtml, editedBy, editReason, editedAt |
| 매퍼 | `BbsPostHistoryMapper.findByPostId` | **존재 (페이지네이션 없음)** | 전체 행 반환 — 본 SPEC에서 페이징 메서드 추가 필요 |
| 매퍼 | `BbsPostHistoryMapper.insert` / `nextVersionByPostId` | **존재 (write 경로)** | 적재 로직 — **변경 금지** |
| 엔드포인트 | `GET /api/v1/board/posts/{postId}` | **존재** | 현재 본문 단건 조회 — 히스토리 엔드포인트는 이 하위 경로로 추가 |
| 인증 | PostController `@PreAuthorize("isAuthenticated()")` | **존재** | 게시글 mutating 엔드포인트 RBAC 패턴 — 동일 패턴 재사용 |
| 페이지네이션 | `PageResponse<T>` (auth.dto) | **존재** | 기존 게시글 목록 API가 사용 — 재사용 |

---

## 3. 본 SPEC이 신규 도입하는 것 (gap 산출물)

| 산출물 | 현재 상태 | 신규/완성 |
|--------|-----------|-----------|
| `BbsPostHistoryMapper` 페이징 조회 + count + 단건 조회 메서드 | 부재 | 신규 (editor 이름 JOIN 포함) |
| `PostHistorySummary` / `PostHistoryDetail` DTO | 부재 | 신규 |
| `PostHistoryService` (또는 `PostService` 확장) read 메서드 | 부재 | 신규 |
| `GET /api/v1/board/posts/{postId}/history` (페이징 목록) | 부재 | 신규 |
| `GET /api/v1/board/posts/{postId}/history/{version}` (단건 버전 본문) | 부재 | 신규 |
| `frontend/admin/src/api/board.ts` 히스토리 함수 | 부재 | 신규 |
| `PostDetailView.vue` 히스토리 탭/모달 | 부재 | 신규 |
| ko/en i18n 키 (`board.postHistory.*`) | 부재 | 신규 |

---

## 4. 범위 및 비범위

### 4.1 범위 (In Scope)

- 게시글별 버전 스냅샷 **목록 조회** 백엔드 API (페이지네이션, version DESC 정렬)
- 목록 항목별 메타데이터: 버전 번호, 수정자 표시명(editor name), 수정 일시(editedAt), 수정 사유(editReason)
- 특정 버전의 **전체 본문(title + content_html)** 단건 조회 백엔드 API
- 관리자 `PostDetailView.vue` 내 히스토리 탭(또는 모달) — 페이징 표 + 행 선택 시 해당 버전 본문 표시
- 수정자 표시명 JOIN (`edited_by` → `users` 테이블)
- 백엔드 RBAC: 게시글 관리 권한자에게만 노출(기존 PostController 인증 패턴 재사용)
- ko/en i18n 키

### 4.2 비범위 (Exclusions — What NOT to Build)

- **버전 복원(restore)·롤백(rollback) 기능** — 본 SPEC은 read-only 뷰어. 과거 버전을 현재 게시글로 되돌리는 기능은 일절 포함하지 않는다.
- **diff 하이라이팅 / 단어·라인 단위 변경 강조** — 본 SPEC은 각 버전의 전체 본문을 그대로 표시한다. 두 버전 간 시각적 diff는 별도 SPEC 대상.
- **공개(public) API / 비관리자 접근** — 히스토리는 관리자 전용. 공개 게시판 사용자에게 노출하지 않는다.
- **신규 DB 마이그레이션 / `bbs_post_history` 스키마·인덱스 변경** — 테이블과 인덱스는 V10에 모두 존재.
- **이력 적재(write) 로직 변경** — `insert`/`nextVersionByPostId`는 변경 금지. 본 SPEC은 조회(read) 전용.
- **이력 보존(retention)·자동 삭제·아카이빙** — 별도 SPEC 대상.
- **첨부파일·번역(i18n 본문)·댓글 버전 이력** — 본 SPEC은 `bbs_post_history`(본문 제목/HTML)만 대상. 첨부·번역·댓글 변경 이력은 범위 외.
- **실시간 푸시/웹소켓** — 폴링/수동 조회 기반.

---

## 5. 신규 요구사항 (REQ-PH-*) — EARS 형식

### 5.1 백엔드 — 목록 조회

- **REQ-PH-001** (Event-Driven): **When** 관리자가 `GET /api/v1/board/posts/{postId}/history?page={p}&size={s}` 를 호출하면, the system **shall** 해당 게시글의 버전 스냅샷을 `version` 내림차순으로 정렬해 `PageResponse` 형태(요소 + 페이징 메타)로 반환한다.

- **REQ-PH-002** (Ubiquitous): The system **shall** 목록 각 항목에 version, editorName(수정자 표시명), editedAt, editReason, title 을 포함한다. content_html 전체 본문은 목록 응답에서 제외한다(목록 경량화).

- **REQ-PH-003** (State-Driven): **While** 스냅샷의 `edited_by` 가 NULL이거나 해당 사용자가 삭제된 경우, the system **shall** editorName 을 null(또는 합의된 기본 표시값)로 반환하고 오류 없이 항목을 포함한다.

### 5.2 백엔드 — 단건 버전 조회

- **REQ-PH-004** (Event-Driven): **When** 관리자가 `GET /api/v1/board/posts/{postId}/history/{version}` 를 호출하면, the system **shall** 해당 (postId, version) 스냅샷의 전체 본문(title + content_html) 및 메타데이터를 단건으로 반환한다.

- **REQ-PH-005** (Unwanted Behavior): **If** 요청한 (postId, version) 조합의 스냅샷이 존재하지 않으면, **then** the system **shall** HTTP 404를 반환하고 빈 본문이나 다른 게시글 데이터를 절대 반환하지 않는다.

### 5.3 백엔드 — 인증/인가

- **REQ-PH-006** (Unwanted Behavior): **If** 인증되지 않은 요청이 히스토리 엔드포인트에 도달하면, **then** the system **shall** 본문을 노출하지 않고 401/403으로 거부한다(기존 PostController `@PreAuthorize` 패턴 준수).

### 5.4 프론트엔드 — 관리자 UI

- **REQ-PH-007** (Event-Driven): **When** 관리자가 게시글 상세(`PostDetailView.vue`)에서 히스토리 탭을 선택하면, the system **shall** 해당 게시글의 버전 목록을 페이징 표로 표시한다(컬럼: 버전, 수정자, 수정 일시, 수정 사유).

- **REQ-PH-008** (Event-Driven): **When** 관리자가 목록의 특정 버전 행을 선택하면, the system **shall** 해당 버전의 본문(제목 + 렌더링된 content_html)을 읽기 전용으로 표시한다.

- **REQ-PH-009** (State-Driven): **While** 해당 게시글에 이력 스냅샷이 하나도 없는 경우, the system **shall** 빈 상태(예: "수정 이력 없음") 메시지를 표시하고 오류를 표시하지 않는다.

- **REQ-PH-010** (Ubiquitous): The system **shall** 히스토리 탭/표/본문 영역에 복원·편집·삭제 등 어떤 mutating 액션 컨트롤도 노출하지 않는다(read-only 보장).

---

## 6. 인수 기준 요약

상세 Given-When-Then 시나리오는 `acceptance.md` 참조. 최소 통과 기준:

- 페이징 목록 API가 version DESC로 정확한 페이지를 반환한다(REQ-PH-001, 002).
- 존재하지 않는 버전 조회 시 404 (REQ-PH-005).
- 비인증 접근 차단 (REQ-PH-006).
- 관리자 UI 히스토리 탭에서 목록 → 버전 본문 조회 흐름이 동작하며 복원/편집 컨트롤이 없다(REQ-PH-007~010).

---

## 7. 의존성

- `bbs_post_history` 테이블 및 `idx_post_history_post` 인덱스 (V10__board_schema.sql) — **선행 존재**
- `users` 테이블 (editor 표시명 JOIN 원천)
- `PageResponse<T>` (kr.co.ircp.cms.domain.auth.dto) — 페이징 응답 재사용
- 기존 게시글 적재 경로(`BbsPostHistoryMapper.insert`) — 이력 데이터가 채워지려면 게시글 수정이 선행되어야 함(데이터 전제)
- 프론트엔드: `PostDetailView.vue`, `frontend/admin/src/api/board.ts`

---

## 8. 기술 설계 개요

상세 구현 계획은 `plan.md` 참조. 핵심 결정:

- **신규 마이그레이션 없음**: 테이블·인덱스 재사용. `idx_post_history_post(post_id, version DESC)` 가 페이징·정렬을 커버.
- **목록 / 단건 분리**: 목록은 content_html 제외(경량), 단건만 전체 본문 반환.
- **editor JOIN**: `bbs_post_history.edited_by LEFT JOIN users` 로 표시명 확보. LEFT JOIN으로 삭제 사용자/NULL 안전(REQ-PH-003).
- **엔드포인트 배치**: 기존 `/api/v1/board/posts/{postId}` 하위에 `/history`, `/history/{version}` 추가 — 게시글 도메인 경로 일관성 유지.
- **RBAC**: PostController의 기존 `@PreAuthorize("isAuthenticated()")` 패턴 재사용(관리자 콘솔 한정 노출).
