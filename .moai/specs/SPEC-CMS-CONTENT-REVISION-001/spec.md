---
id: SPEC-CMS-CONTENT-REVISION-001
version: 0.1.0
status: draft
created: 2026-06-25
updated: 2026-06-25
author: manager-spec
priority: medium
issue_number: TBD
labels: [cms, content, revision, diff, optimistic-lock]
related:
  - SPEC-CMS-POST-HISTORY-001 (게시물 read-only 이력 뷰어 — diff/롤백/충돌감지 제외분을 본 SPEC이 보강)
  - REQ-CONTENT-005-D (페이지 이력·롤백 기구현 — 본 SPEC이 diff·낙관적잠금·retention 보강)
---

# SPEC-CMS-CONTENT-REVISION-001 — 콘텐츠 수정 이력(Revision) 관리

## HISTORY

- 2026-06-25 (v0.1.0): Draft 작성. 게시물(`bbs_post`/`bbs_post_history`, V10)과 페이지(`page`/`page_history`, V13)의 **이미 분리된 이력 인프라** 위에 공통 갭 — 두 버전 diff 비교, 게시물 롤백, 진짜 낙관적 잠금(409), revision 보존 정책(retention), 통합 Revision UI — 을 정의. 게시물 낙관적 잠금용 `bbs_post.version` 컬럼 추가(V54) 외 신규 테이블 없음. `system_setting`(V14) retention 정책 재사용.

---

## 1. 개요

### 1.1 목적

관리자가 게시물(Post)과 페이지(Page)의 **수정 이력(Revision)을 풍부하게 관리**할 수 있게 한다. 구체적으로 (1) 임의의 두 버전을 라인별 diff로 비교, (2) 특정 버전으로 롤백(게시물 신규·페이지 재사용), (3) 동시 편집 시 충돌 감지(낙관적 잠금, 409), (4) 엔티티당 revision 보존 정책, (5) 편집 화면 내 통합 Revision 사이드패널·diff 뷰어를 제공한다.

### 1.2 배경 (실제 코드베이스 기반 gap)

> 상세 조사는 `research.md` 참조. 프롬프트 전제 일부는 실제 코드와 달라 정정했다.

- **게시물**: `bbs_post_history`(V10) 적재 + `SPEC-CMS-POST-HISTORY-001`(Completed) read-only 뷰어가 존재한다. 그러나 diff·롤백·충돌감지는 POST-HISTORY-001이 **명시적으로 제외**했다. `bbs_post`에는 낙관적 잠금용 version 컬럼이 **없다**.
- **페이지**: `page`/`page_history`(V13)와 `PageService.getPageHistory`·`rollbackPage`가 **이미 구현**되어 있다(`REQ-CONTENT-005-D`). 그러나 두 버전 diff 비교가 없고, `page.current_version`은 단순 카운터로 `UPDATE` 시 검증되지 않아 **lost update(동시 편집 덮어쓰기)** 가 발생할 수 있다.
- **공통**: 두 도메인 모두 diff·retention·진짜 낙관적 잠금이 없다. diff 라이브러리는 백엔드·프론트 어디에도 없다.

본 SPEC은 이 공통 갭을 두 도메인에 병렬로 보강한다. **`SPEC-CMS-PAGE-HISTORY-001`은 존재하지 않으며, 페이지 롤백은 기존 `PageService.rollbackPage`를 재사용한다.**

### 1.3 범위 요약

게시물·페이지 양 도메인에 대해: 두 버전 diff 조회 API + UI, 게시물 롤백(페이지는 재사용), 저장 시 낙관적 잠금 충돌 감지(409), revision 보존 정책, 편집 화면 통합 Revision 패널. 신규 DB 마이그레이션은 **`bbs_post.version` 컬럼 추가 1건(V54)** 뿐이다.

---

## 2. 이미 존재하는 것 (재사용 인프라)

| 구분 | 항목 | 상태 | 본 SPEC에서의 취급 |
|------|------|------|-------------------|
| DB | `bbs_post_history` (V10): post_id, version, title, content_html, edited_by, edit_reason, edited_at | 존재 | diff·롤백 read 원천으로 재사용 |
| DB | `page_history` (V13): page_id, version, snapshot(JSONB), edited_by, change_summary | 존재 | diff·롤백 read 원천으로 재사용 |
| DB | `page.current_version` 카운터 | 존재 | **낙관적 잠금 값으로 의미 확장** (스키마 변경 없음) |
| DB | `system_setting` (V14, key/value/value_type) | 존재 | retention 정책값(max N) 저장 — **신규 테이블 불필요** |
| 백엔드 | `PostHistoryService` (목록/단건 read) | 존재 (POST-HISTORY-001) | diff·롤백 메서드 추가 대상 |
| 백엔드 | `PageService.getPageHistory` / `rollbackPage` | 존재 | **페이지 롤백 그대로 재사용**, diff·낙관락만 보강 |
| 백엔드 | `BbsPostHistoryMapper` (write+read) | 존재 | write(insert/nextVersion) **변경 금지**, diff용 단건 2개 fetch 재사용 |
| 백엔드 | `PageResponse<T>` 페이징 DTO (auth.dto) | 존재 | 재사용 |
| 인증 | `PAGE:HISTORY:READ`, `PAGE:ROLLBACK`, PostController `isAuthenticated()` | 존재 | diff·게시물 롤백 권한 패턴 재사용 |
| 프론트 | `PostDetailView.vue` 히스토리 탭, `PageEditorView.vue` | 존재 | Revision 패널·diff 뷰어 마운트 지점 |

---

## 3. 본 SPEC이 신규 도입하는 것 (gap 산출물)

| 산출물 | 현재 상태 | 신규/완성 |
|--------|-----------|-----------|
| `bbs_post.version` 낙관적 잠금 컬럼 (V54) | 부재 | 신규 (페이지는 current_version 재사용) |
| 두 버전 diff 계산기 (라인별, title/content/slug) | 부재 (라이브러리 전무) | 신규 (백엔드 계산) |
| `GET .../history/diff?from={a}&to={b}` 게시물·페이지 diff API | 부재 | 신규 |
| 게시물 롤백 `POST /api/v1/board/posts/{postId}/rollback/{version}` | 부재 | 신규 (페이지 패턴 모방) |
| 낙관적 잠금 충돌 → `409 Conflict` + 서버 최신 버전 응답 | 부재 (lost update 가능) | 신규 (게시물·페이지 update 경로) |
| revision 보존 정책 (max N, system_setting 기반) | 부재 | 신규 |
| 공유 `RevisionPanel` + `DiffViewer` 프론트 컴포넌트 | 부재 | 신규 (게시물·페이지 양쪽 마운트) |
| ko/en i18n 키 (`content.revision.*`) | 부재 | 신규 |

---

## 4. 데이터 모델 (Data Model)

### 4.1 신규 — `bbs_post.version` (V54 마이그레이션)

```sql
ALTER TABLE bbs_post ADD COLUMN version INT NOT NULL DEFAULT 1;
COMMENT ON COLUMN bbs_post.version IS '낙관적 잠금 버전. 수정 시 +1. UPDATE ... WHERE id=? AND version=? 로 동시편집 충돌 감지. SPEC-CMS-CONTENT-REVISION-001';
```

기존 행은 DEFAULT 1로 채워진다. `bbs_post_history.version`(스냅샷 버전)과는 별개 개념 — 전자는 현재 행의 낙관락 카운터, 후자는 과거 스냅샷 번호다(현실적으로 동일 값으로 동기화 운영 가능, plan에서 결정).

### 4.2 재사용 — `page.current_version`

스키마 변경 없음. 수정 시 `UPDATE page SET ..., current_version = current_version + 1 WHERE id=? AND current_version=?` 형태로 낙관적 잠금에 활용한다.

### 4.3 재사용 — `system_setting` retention 정책

```sql
INSERT INTO system_setting(key, value, value_type, description)
VALUES ('content.revision.maxPerEntity', '50', 'INT',
        '게시물/페이지당 보존할 최대 revision 수. 초과 시 가장 오래된 version 삭제. SPEC-CMS-CONTENT-REVISION-001')
ON CONFLICT (key) DO NOTHING;
```

### 4.4 diff 응답 모델 (신규 DTO, 테이블 없음)

라인 단위 diff 결과: `List<DiffLine{ type: EQUAL|ADD|DELETE, oldLineNo, newLineNo, text }>` 를 필드별(title/content/slug)로 묶은 `RevisionDiffResponse{ field, fromVersion, toVersion, lines }`.

---

## 5. 신규 요구사항 (REQ-REV-*) — EARS 형식

### REQ-REV-001 — Revision 자동 생성 (Event-Driven)

**When** 관리자가 게시물(Post) 또는 페이지(Page)를 저장(수정)하면, the system **shall** 저장 직전 내용의 불변 스냅샷을 해당 이력 테이블(`bbs_post_history` / `page_history`)에 새 version으로 적재하고, 현재 행의 낙관적 잠금 version(`bbs_post.version` / `page.current_version`)을 1 증가시킨다.

> 비고: 게시물·페이지의 **기존 적재 경로는 보존**한다. 본 REQ는 적재 시점에 낙관락 version 증가가 함께 일어남을 보장한다(중복 적재 로직 신설 금지).

### REQ-REV-002 — Revision 목록 조회 (Event-Driven)

**When** 관리자가 특정 게시물/페이지의 revision 목록을 요청하면, the system **shall** version 내림차순으로 각 항목의 version, 수정자 표시명(editorName), 저장 일시, 변경 요약(edit_reason / change_summary)을 반환한다. (게시물은 기존 `GET /api/v1/board/posts/{postId}/history` 페이징, 페이지는 기존 `GET /api/v1/content/pages/{id}/history`를 재사용한다.)

### REQ-REV-003 — Diff 비교 (Event-Driven)

**When** 관리자가 한 엔티티의 두 revision(`from`, `to`)에 대해 diff를 요청하면, the system **shall** 제목·본문(게시물=content_html, 페이지=직렬화 콘텐츠)·슬러그(페이지 전용)에 대해 라인별 변경(추가/삭제/동일)을 계산해 반환한다.

> 게시물에는 slug가 없으므로 slug diff는 페이지에만 적용된다. 페이지 본문은 `page_history.snapshot`(JSONB)을 표시용 텍스트로 평탄화한 뒤 diff한다.

### REQ-REV-004 — Revision 롤백 (Event-Driven)

**When** 관리자가 특정 revision으로 롤백을 요청하면, the system **shall** 해당 스냅샷 내용을 현재 콘텐츠로 복원하고, 롤백 자체를 **새로운 revision으로 추가 적재**한다(과거 version 파괴 금지). 페이지는 기존 `PageService.rollbackPage`를 재사용하고, 게시물은 동일 패턴(`POST /api/v1/board/posts/{postId}/rollback/{version}`)을 신규 구현한다.

### REQ-REV-005 — 동시 편집 충돌 감지 (Unwanted Behavior)

**If** 두 명 이상이 같은 게시물/페이지를 동시에 편집해 나중 저장 요청이 자신이 읽은 version과 현재 서버 version이 불일치하는 상태로 도달하면, **then** the system **shall** 저장을 거부하고 `409 Conflict`와 함께 서버의 현재 version 및 충돌 정보를 반환하며, 사용자의 변경으로 기존 내용을 덮어쓰지 않는다.

> 구현: `UPDATE ... WHERE id=? AND version=?` 의 영향 행 수가 0이면 충돌. 클라이언트는 수정 요청에 자신이 로드한 version(`expectedVersion`)을 포함해야 한다.

### REQ-REV-006 — Revision 보존 정책 (State-Driven)

**While** 한 게시물/페이지의 revision 수가 설정값 N(`system_setting` 키 `content.revision.maxPerEntity`, 기본 50)을 초과하면, the system **shall** 가장 오래된 version부터 초과분을 삭제해 N개 이하로 유지한다. 보존 정리는 best-effort이며 실패하더라도 저장(적재) 자체를 막지 않는다.

### REQ-REV-007 — 프론트엔드 Revision UI (Event-Driven)

**When** 관리자가 게시물(`PostDetailView.vue`/`PostFormView.vue`) 또는 페이지(`PageEditorView.vue`) 편집 화면에서 Revision 패널을 열면, the system **shall** revision 이력 목록을 표시하고, 두 항목 선택 시 diff 뷰어로 라인별 변경을 시각화하며, 롤백 액션을 제공한다. 충돌(409) 발생 시 사용자에게 충돌 안내와 서버 최신본 비교 경로를 제시한다.

---

## 6. 범위 및 비범위

### 6.1 범위 (In Scope)

- 게시물·페이지 두 버전 라인별 diff(제목/본문, 페이지는 슬러그 추가) 백엔드 계산 + diff 조회 API
- 게시물 롤백 신규 구현(페이지 롤백은 기존 재사용), 롤백 = 새 revision 적재
- 게시물·페이지 저장 경로의 낙관적 잠금(409) — `bbs_post.version` 신규 + `page.current_version` 활용
- revision 보존 정책(max N, `system_setting` 기반) 적용
- 공유 `RevisionPanel` + `DiffViewer` 프론트 컴포넌트(게시물/페이지 양쪽 마운트), ko/en i18n
- `bbs_post.version` 추가 마이그레이션(V54) 1건

### 6.2 비범위 (Exclusions — What NOT to Build)

- **게시물·페이지를 합친 단일 통합 Revision 테이블/도메인 신설** — 두 스키마(개별 컬럼 vs JSONB 스냅샷)가 근본적으로 다르므로 공통 추상화는 만들지 않는다. 각 도메인에 병렬 보강한다.
- **단어 단위(word-level)·문자 단위 inline diff** — 본 SPEC은 라인 단위 diff만. 토큰·구문 강조 diff는 범위 외.
- **3-way 머지·충돌 자동 병합** — 충돌은 감지(409)만 하고 사용자 수동 재작업으로 해결한다. 자동 머지는 일절 포함하지 않는다.
- **첨부파일·번역(i18n 본문)·댓글의 버전 이력/diff/롤백** — 본 SPEC은 본문 제목/HTML(게시물), page+block 스냅샷(페이지)만 대상.
- **공개(public)/비관리자 접근** — revision·diff·롤백은 관리자 전용. 시민 라우팅에 노출하지 않는다.
- **실시간 협업 편집·웹소켓·편집 잠금(pessimistic lock)** — 낙관적 잠금 + 폴링/수동 조회 기반. 실시간 동시편집 표시는 범위 외.
- **revision 아카이빙/외부 스토리지 이관** — retention 초과분은 단순 삭제. 아카이빙은 별도 SPEC.
- **페이지 `page_history` / 게시물 `bbs_post_history` 스키마 변경** — 두 이력 테이블 스키마는 변경하지 않는다(컬럼 추가는 `bbs_post` 본 테이블에 한함).
- **기존 적재(write) 로직의 재작성** — `BbsPostHistoryMapper.insert`, `PageService.updatePage`의 이력 누적은 보존하며, 낙관락 version 증가만 통합한다.

---

## 7. 인수 기준 요약

상세 Given-When-Then 시나리오는 `acceptance.md` 참조. 최소 통과 기준:

- 저장 시 revision 적재 + 낙관락 version 증가(REQ-REV-001).
- 목록이 version DESC + editorName 포함 반환(REQ-REV-002).
- 두 버전 diff가 추가/삭제/동일 라인을 정확히 표기(REQ-REV-003).
- 게시물·페이지 롤백이 내용 복원 + 새 revision 추가(REQ-REV-004).
- 구버전 version으로 저장 시 409 + 서버 최신 version 반환, 덮어쓰기 없음(REQ-REV-005).
- N 초과 시 가장 오래된 version 삭제로 N 유지, 적재는 항상 성공(REQ-REV-006).
- 편집 화면 Revision 패널 → diff 뷰어 → 롤백 흐름 동작, 409 안내(REQ-REV-007).

---

## 8. 의존성

- `bbs_post_history`(V10), `page_history`/`page.current_version`(V13) — 선행 존재
- `SPEC-CMS-POST-HISTORY-001` read API, `PageService.getPageHistory`/`rollbackPage` — 재사용
- `system_setting`(V14) — retention 정책 저장
- `PageResponse<T>`(auth.dto), `users` 테이블(editor 표시명 JOIN)
- 프론트: `PostDetailView.vue`, `PostFormView.vue`, `PageEditorView.vue`, `api/board.ts`, `api/content.ts`
- 마이그레이션: main 최신 V53 → 신규 V54. **run 직전 main 최신 재확인 후 충돌 시 재번호**(미머지 브랜치가 V54+ 잠정 점유).

## 9. 기술 설계 개요

상세는 `plan.md` 참조. 핵심 결정:

- **도메인 분리 유지**: 통합 추상화 금지. 게시물(`board`)·페이지(`content.page`)에 diff/롤백/낙관락 병렬 추가.
- **낙관적 잠금**: 게시물 `version` 신규 컬럼, 페이지 `current_version` 재사용. 둘 다 `UPDATE WHERE version=?` → 0행 시 409.
- **diff**: 백엔드 라인 LCS 계산(서버 단일 진실). 페이지 JSONB snapshot은 표시용 텍스트 평탄화 후 diff.
- **롤백 불변성**: 롤백도 새 revision 적재. 과거 version 파괴 금지.
- **retention**: `system_setting` N 설정, best-effort 정리.
- **UI**: 공유 `RevisionPanel`+`DiffViewer` 컴포넌트를 게시물/페이지 화면에 각각 마운트.
