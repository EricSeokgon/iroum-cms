---
id: SPEC-CMS-NOTICE-I18N-001
version: 0.1.0
status: Implemented
created: 2026-06-01
updated: 2026-06-01
author: manager-spec
priority: P2
parent: SPEC-CMS-008
related:
  - SPEC-CMS-008 (게시판 시스템 — bbs_master/bbs_post 원천)
  - SPEC-CMS-013 (콘텐츠 스키마 — i18n_resource 패턴 참조)
issue_number: TBD
---

# SPEC-CMS-NOTICE-I18N-001 — 공지사항 다중 언어 지원 (Notice i18n)

## HISTORY

- 2026-06-01 (v0.1.0): Draft 작성. 공지(NOTICE) 게시글의 한국어(ko)+영어(en) 다국어 등록/조회 요구사항 정의. `bbs_post_i18n` 신규 분리 테이블 접근법 채택.

---

## 1. 개요

### 1.1 목적

관리자가 공지사항(`bbs_master.type = 'NOTICE'`) 게시글을 한국어(ko)와 영어(en) 두 가지 언어로 등록·관리하고, 시민 사용자가 공개 API에 언어 파라미터(`?lang=en`)를 전달해 해당 언어의 콘텐츠를 조회할 수 있도록 한다.

### 1.2 배경

- 현재 `bbs_post`는 단일 언어(`title`, `content_html`, `content_text`) 구조이며 다국어 표현 수단이 없다.
- 기존 `i18n_resource` 테이블(V13)은 `namespace` CHECK 제약이 `'menu'/'page'/'popup'/'banner'/'content_block'/'system'`으로 한정되어 있어 `bbs_post`를 수용하지 못한다. 즉 게시글 다국어는 기존 인프라로 처리 불가능하다.
- 공공기관 사이트 특성상 공지사항의 영문 제공 요구가 발생하나, 전체 게시판 타입(NORMAL/QNA/FAQ 등)에는 다국어가 불필요하다.

### 1.3 범위 요약

NOTICE 타입 게시글에 한해 ko(기본)+en(선택) 2개 언어를 지원한다. 영어 번역은 별도 테이블(`bbs_post_i18n`)에 저장하며, 영어 번역이 없으면 한국어로 폴백(fallback)한다.

---

## 2. 기존 인프라 (재사용 항목)

| 항목 | 위치 | 재사용 방식 |
|------|------|------------|
| `bbs_master` (type='NOTICE') | V10__board_schema.sql | 변경 없음. NOTICE 게시판 식별에 사용 |
| `bbs_post` (title, content_html, content_text) | V10__board_schema.sql | 변경 없음. 한국어(ko) 원본으로 간주 |
| `boardApi` (frontend/admin/src/api/board.ts) | 관리자 API 래퍼 | 번역 메서드 추가로 확장 |
| `PostFormView.vue` (Tiptap 에디터) | frontend/admin/src/views/board | 언어 탭 구조 추가로 확장 |
| 공개 게시글 조회 API (SPEC-CMS-PUBLIC-001) | 공개 컨트롤러 | `?lang` 파라미터 추가로 확장 |
| `site.supported_languages` (`["ko","en"]`) | V13__content_schema.sql | 지원 언어 목록 참조 |

---

## 3. 신규 도입 (Gap)

| Gap | 신규 항목 | 근거 |
|-----|----------|------|
| 게시글 다국어 저장소 부재 | `bbs_post_i18n` 테이블 (V41 마이그레이션) | `i18n_resource`는 namespace 제약으로 bbs_post 수용 불가; `bbs_post` 직접 컬럼 추가는 전체 게시판 타입에 NULL 컬럼 부담 |
| 관리자 다국어 입력 UI 부재 | PostFormView 언어 탭 (`el-tabs`) | 한국어 탭(필수) / English 탭(선택) |
| 번역 저장/삭제 API 부재 | `/api/v1/board/posts/{id}/translations` 계열 엔드포인트 | 기존 `/board` API 컨벤션 준수 |
| 공개 언어 협상 부재 | 공개 API `?lang` 파라미터 + `Accept-Language` 폴백 | 시민 사용자 언어 선택 |

### 3.1 아키텍처 결정 (별도 테이블 채택 근거)

- **별도 테이블 방식**: `bbs_post_i18n(post_id, language, title, content_html, content_text, updated_at)`을 신규 V41 마이그레이션으로 도입한다.
- **왜 분리하는가**: `bbs_post` 컬럼 추가는 모든 게시판 타입(NORMAL/QNA/FAQ/GALLERY 등)에 영향을 주며, 다국어가 불필요한 게시글에 NULL 컬럼을 강제한다. 분리 테이블은 NOTICE에만 행이 존재하도록 하여 부담을 제거한다.
- **한국어(ko)가 1차 원천**: 기존 `bbs_post.title` + `content_html` + `content_text`가 한국어 버전이다. 데이터 마이그레이션이 불필요하다.
- **영어(en)는 분리 저장**: `bbs_post_i18n`에 `language='en'` 행으로 저장한다.
- **폴백**: 영어 번역이 없으면 한국어 원본을 반환한다.

> 참고(현행 코드와의 정합): 관리자 게시글은 `content_html`(Tiptap) + 자동 생성되는 `content_text`로 저장된다. 번역 테이블도 동일하게 `content_html`을 1차 입력으로 받고 `content_text`를 함께 보관한다. 관리자 API base는 `/api/v1/board` 이므로 번역 엔드포인트는 `/api/v1/board/posts/{id}/translations`로 한다.

---

## 4. 범위 및 비범위

### 4.1 범위 (In Scope)

- NOTICE 타입 게시글의 ko+en 2개 언어 등록/수정/삭제
- 공개 단건 조회 API의 언어별 콘텐츠 반환 (영어 우선, 한국어 폴백)
- 공개 목록 조회 API의 언어별 제목 반환
- 관리자 폼의 언어 탭 UI 및 목록의 번역 완료 배지
- `Content-Language` 응답 헤더로 실제 반환 언어 명시

### 4.2 비범위 (Out of Scope) — What NOT to Build

- **NOTICE 외 게시판 타입(NORMAL/QNA/FAQ/GALLERY/PUBLICATION/SURVEY)의 다국어 지원** — 본 SPEC은 NOTICE에 한정한다.
- **ko/en 외 제3언어(일본어/중국어 등) 지원** — `bbs_post_i18n.language` CHECK는 ('ko','en')으로 제한한다.
- **댓글(`bbs_comment`)·첨부파일(`bbs_attachment`)의 다국어** — 게시글 본문/제목만 대상.
- **자동 기계 번역 연동** — 번역문은 관리자가 직접 입력한다.
- **검색 인덱스(`search_vector`)의 다국어 토큰화** — 기존 한국어 검색 동작을 변경하지 않는다.
- **한국어 원본 데이터 마이그레이션** — 기존 `bbs_post` 행은 그대로 둔다.
- **공개 사이트(citizen) 프론트엔드 언어 토글 UI** — 본 SPEC은 백엔드 API + 관리자 UI까지. 시민 프론트 토글은 후속 SPEC.

---

## 5. 신규 요구사항 (REQ-NI-*) — EARS 형식

### REQ-NI-001 (Ubiquitous) — 다국어 저장 테이블

The system **shall** provide a `bbs_post_i18n` table with columns (`post_id` FK to `bbs_post` ON DELETE CASCADE, `language` CHECK IN ('ko','en'), `title`, `content_html`, `content_text`, `updated_at`) and a UNIQUE constraint on (`post_id`, `language`).

### REQ-NI-002 (Event-Driven) — 관리자 언어 탭 UI

**When** an administrator opens the notice create or edit form, the system **shall** display language tabs (한국어 tab required, English tab optional) for entering title and content per language.

### REQ-NI-003 (Event-Driven) — 영어 번역 저장

**When** an administrator saves content in the English tab, the system **shall** persist a row with `language='en'` in `bbs_post_i18n` via `POST/PUT /api/v1/board/posts/{id}/translations/en`.

### REQ-NI-004 (Event-Driven) — 공개 단건 언어 조회 + 폴백

**When** a citizen requests `GET /api/v1/public/posts/{id}?lang=en`, the system **shall** return the English title/content if a translation exists; **if** no English translation exists, **then** the system **shall** return the Korean original and set the `Content-Language: ko` response header.

### REQ-NI-005 (Event-Driven) — 공개 목록 언어 조회

**When** a citizen requests `GET /api/v1/public/posts?bbs=NOTICE&lang=en`, the system **shall** return each item's English title if its English translation exists, otherwise the Korean title.

### REQ-NI-006 (State-Driven) — 번역 완료 배지

**While** an English translation row exists for a notice, the system **shall** display an 'EN' translation-complete badge for that item in the administrator notice list.

### REQ-NI-007 (Unwanted Behavior) — 한국어 필수 불변식

**If** an administrator attempts to save a notice with an empty Korean title (한국어 탭 제목 미입력), **then** the system **shall** reject the save (disable the save action) and **shall not** allow an English-only notice to be created.

### REQ-NI-008 (Event-Driven) — 번역 개별 삭제

**When** an administrator requests `DELETE /api/v1/board/posts/{id}/translations/en`, the system **shall** delete the English translation row, after which `GET .../posts/{id}?lang=en` **shall** fall back to the Korean original.

### REQ-NI-009 (State-Driven) — 비-NOTICE 게시글 보호

**While** a post's board type is not NOTICE, the system **shall** reject translation creation requests for that post (translations are only allowed on NOTICE-type posts).

### REQ-NI-010 (Event-Driven) — 언어 협상 우선순위

**When** a public request provides both a `?lang` query parameter and an `Accept-Language` header, the system **shall** prioritize the `?lang` parameter; **where** `?lang` is absent, the system **shall** use `Accept-Language` as fallback, defaulting to `ko`.

---

## 6. 수락 기준 (AC-NI-*)

### REQ-NI-001 (테이블)
- **AC-NI-001-1**: V41 마이그레이션 실행 후 `bbs_post_i18n` 테이블이 존재하고, (`post_id`, `language`) UNIQUE 제약이 조회된다.
- **AC-NI-001-2**: `language` 컬럼에 'ja' 등 ('ko','en') 외 값 INSERT 시 CHECK 위반으로 거부된다.
- **AC-NI-001-3**: `bbs_post` 행 삭제 시 연관 `bbs_post_i18n` 행이 CASCADE 삭제된다.

### REQ-NI-002 (관리자 탭 UI)
- **AC-NI-002-1**: PostFormView에 한국어/English 탭이 표시된다.
- **AC-NI-002-2**: 한국어 탭의 제목이 비어 있으면 저장 버튼이 비활성화된다.
- **AC-NI-002-3**: 수정 모드 진입 시 한국어 탭에는 `bbs_post` 원본이, English 탭에는 기존 영어 번역(존재 시)이 프리필된다.

### REQ-NI-003 (영어 저장)
- **AC-NI-003-1**: English 탭에 제목/내용 입력 후 저장하면 `bbs_post_i18n`에 `language='en'` 행이 INSERT 된다.
- **AC-NI-003-2**: 이미 영어 번역이 있는 게시글을 다시 저장하면 기존 `language='en'` 행이 UPDATE 된다(중복 INSERT 안 됨).
- **AC-NI-003-3**: English 탭을 비운 채 저장하면 영어 번역 행이 생성되지 않는다(또는 기존 행이 변경되지 않는다).

### REQ-NI-004 (단건 폴백)
- **AC-NI-004-1**: `GET .../posts/42?lang=en` → 영어 번역 존재 시 영어 title/content를 반환하고 `Content-Language: en` 헤더를 설정한다.
- **AC-NI-004-2**: `GET .../posts/42?lang=en` → 영어 번역 미존재 시 한국어 원본을 반환하고 `Content-Language: ko` 헤더를 설정한다.
- **AC-NI-004-3**: `GET .../posts/42` (lang 미지정) → 한국어 원본을 반환하고 `Content-Language: ko` 헤더를 설정한다.

### REQ-NI-005 (목록)
- **AC-NI-005-1**: `GET .../posts?bbs=NOTICE&lang=en` → 영어 번역이 있는 항목은 en 제목, 없는 항목은 ko 제목으로 목록을 반환한다.
- **AC-NI-005-2**: 목록 응답의 각 항목에 실제 반환된 언어를 식별할 수 있는 필드(예: `language`)가 포함된다.

### REQ-NI-006 (배지)
- **AC-NI-006-1**: 관리자 공지 목록에서 영어 번역이 존재하는 항목에 'EN' 배지가 표시된다.
- **AC-NI-006-2**: 영어 번역이 없는 항목에는 'EN' 배지가 표시되지 않는다.

### REQ-NI-007 (한국어 필수)
- **AC-NI-007-1**: 영어 제목만 입력하고 한국어 제목을 비운 상태에서는 저장 버튼이 비활성화된다.
- **AC-NI-007-2**: 백엔드에서도 한국어 원본(`bbs_post.title`)이 비어 있는 생성 요청은 검증 오류로 거부된다.

### REQ-NI-008 (삭제)
- **AC-NI-008-1**: `DELETE .../posts/42/translations/en` → `bbs_post_i18n`의 해당 (42,'en') 행이 삭제된다.
- **AC-NI-008-2**: 삭제 후 `GET .../posts/42?lang=en` → 한국어 폴백 + `Content-Language: ko`.
- **AC-NI-008-3**: 존재하지 않는 번역 삭제 요청은 멱등하게 처리된다(404 또는 204, 일관 정책 적용).

### REQ-NI-009 (NOTICE 한정)
- **AC-NI-009-1**: NORMAL 타입 게시글에 대한 `POST .../translations/en` 요청은 거부된다(예: 409/422).

### REQ-NI-010 (언어 협상)
- **AC-NI-010-1**: `?lang=en` + `Accept-Language: ko` 동시 제공 시 영어가 반환된다(쿼리 우선).
- **AC-NI-010-2**: `?lang` 미지정 + `Accept-Language: en` 시 영어가 반환된다(헤더 폴백).
- **AC-NI-010-3**: 둘 다 미지정 시 한국어가 반환된다(기본값).

---

## 7. 기술 접근법

### 7.1 마이그레이션 (V41)

- 파일: `backend/src/main/resources/db/migration/V41__bbs_post_i18n.sql`
- 기존 테이블 **변경 없음**. 신규 테이블만 생성.

```
CREATE TABLE bbs_post_i18n (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id      BIGINT      NOT NULL REFERENCES bbs_post(id) ON DELETE CASCADE,
    language     VARCHAR(10) NOT NULL,
    title        VARCHAR(500) NOT NULL,
    content_html TEXT        NOT NULL,
    content_text TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bbs_post_i18n      UNIQUE (post_id, language),
    CONSTRAINT chk_bbs_post_i18n_lang CHECK (language IN ('ko','en'))
);
CREATE INDEX idx_bbs_post_i18n_post ON bbs_post_i18n(post_id, language);
```

> 참고: `language='ko'` 행은 1차 출시에서 사용하지 않는다(한국어는 `bbs_post`가 원천). CHECK에 'ko'를 포함시키는 것은 향후 확장(한국어를 i18n 테이블로 정규화)을 막지 않기 위함이다.

### 7.2 API 변경

- **관리자 (base `/api/v1/board`)**:
  - `PUT /api/v1/board/posts/{id}/translations/en` — 영어 번역 생성/수정 (upsert)
  - `GET /api/v1/board/posts/{id}/translations` — 해당 게시글 번역 목록(관리자 편집용 프리필)
  - `DELETE /api/v1/board/posts/{id}/translations/en` — 영어 번역 삭제
- **공개 (base `/api/v1/public`)**:
  - `GET /api/v1/public/posts/{id}` — `?lang` 파라미터 추가
  - `GET /api/v1/public/posts?bbs=NOTICE` — `?lang` 파라미터 추가
- **언어 결정 로직**: `?lang` 쿼리 → `Accept-Language` 헤더 → `ko` 기본값. 결정된 언어는 `Content-Language` 응답 헤더로 명시.

### 7.3 프론트엔드 (관리자 UI)

- `PostFormView.vue`: 제목/내용 영역을 `el-tabs`로 감싸 한국어 탭(필수) / English 탭(선택) 구성.
  - 한국어 탭: 기존 `form.title` / `form.contentHtml` 사용 (검증 규칙 유지: 한국어 제목 required).
  - English 탭: `form.translations.en.title` / `form.translations.en.contentHtml` (선택 입력).
  - 저장 시: 한국어는 기존 createPost/updatePost, 영어는 번역 API로 분리 호출. 영어 탭이 비어 있으면 번역 호출 생략.
- `boardApi` (board.ts): `upsertTranslation(id, lang, req)`, `getTranslations(id)`, `deleteTranslation(id, lang)` 추가.
- `PostListView.vue`: 영어 번역 존재 항목에 'EN' 배지 표시(목록 응답에 `hasEnTranslation` 또는 별도 조회).

### 7.4 NOTICE 한정 가드

- 번역 API는 대상 게시글의 `bbs_master.type = 'NOTICE'` 여부를 검증하고, 아니면 거부(REQ-NI-009).

---

## 8. 구현 파일 목록

### Backend
- `backend/src/main/resources/db/migration/V41__bbs_post_i18n.sql` (신규)
- 게시글 번역 엔티티/리포지터리 (신규, `bbs_post_i18n` 매핑)
- 게시글 번역 관리자 컨트롤러/서비스 (신규, `/api/v1/board/posts/{id}/translations`)
- 공개 게시글 컨트롤러/서비스 (수정, `?lang` 파라미터 + 폴백 + `Content-Language` 헤더)
- NOTICE 타입 가드 검증 로직 (수정/신규)

### Frontend (admin)
- `frontend/admin/src/api/board.ts` (수정: 번역 메서드 추가)
- `frontend/admin/src/views/board/PostFormView.vue` (수정: 언어 탭)
- `frontend/admin/src/views/board/PostListView.vue` (수정: EN 배지)
- `@iroum/shared/types/api` 타입 정의 (수정: 번역 요청/응답 타입)

### Tests
- V41 마이그레이션 통합 테스트
- 번역 API 통합 테스트 (생성/수정/삭제/폴백/NOTICE 가드)
- PostFormView 언어 탭 컴포넌트 테스트
- 공개 API 언어 협상 테스트

---

## 9. 미해결 가정 (Assumptions)

1. 공개 게시글 API는 SPEC-CMS-PUBLIC-001에 존재하며 `?lang` 파라미터 추가가 가능하다 — Run 단계에서 실제 컨트롤러 경로 확인 필요.
2. 관리자 번역 권한은 기존 게시글 쓰기 권한(BOARD/POST WRITE)에 포섭된다고 가정한다 — 별도 권한 코드 신설은 비범위.
3. `content_text`는 백엔드에서 `content_html`로부터 파생 저장하거나 클라이언트가 함께 전달한다(기존 `bbs_post` 컨벤션 추종).
