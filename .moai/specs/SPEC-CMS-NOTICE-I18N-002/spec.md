---
id: SPEC-CMS-NOTICE-I18N-002
version: 0.1.0
status: Implemented
created: 2026-06-09
updated: 2026-06-09
author: MoAI
priority: P2
parent: SPEC-CMS-NOTICE-I18N-001
related:
  - SPEC-CMS-NOTICE-I18N-001 (공지 다국어 — bbs_post_i18n 테이블/번역 CRUD/단건 ?lang 원천)
issue_number: TBD
---

# SPEC-CMS-NOTICE-I18N-002 — 공지 목록 API 다국어 제목 (Notice List i18n)

## HISTORY

- 2026-06-09 (v0.1.0): Draft 작성. 부모 SPEC-CMS-NOTICE-I18N-001의 미구현 수락 기준(AC-NI-005-1, AC-NI-005-2)을 후속 SPEC으로 분리. 목록 API의 `?lang=en` 제목 번역 + 폴백 + `language` 필드 추가 정의.

---

## 1. 배경 (Background)

부모 SPEC-CMS-NOTICE-I18N-001은 공지 게시글의 번역 CRUD와 단건 조회(`GET .../posts/{id}?lang=en`)의 언어별 콘텐츠 반환·폴백을 구현했다. 그러나 **목록 API**(`GET .../posts?bbs=NOTICE&lang=en`)는 영어 번역이 존재하는 항목에 대해서도 여전히 한국어 제목을 반환한다. 이는 CHANGELOG v2.5.0에 알려진 제약(known limitation)으로 명시되어 있다.

현행 코드 정합 확인(Run 단계 그라운딩):

- 컨트롤러 `PostController.listPosts(...)`는 이미 `@RequestParam(value = "lang", defaultValue = "ko")`를 받지만, 주석대로 "en 등 번역 목록 오버레이는 후속 작업"으로 남겨져 `lang`을 서비스로 전달하지 않는다.
- 서비스 `PostServiceImpl.listPosts(bbsMasterId, page, size)` / `searchPosts(...)`는 `lang` 인자가 없고, `toSummary(BbsPost p)`가 `bbs_post` 원본 제목만 매핑한다.
- 목록 응답 DTO는 `PostSummary`(record)이며 언어 식별 필드가 없다.
- 영어 번역은 `bbs_post_i18n`(`post_id`, `language='en'`, `title`, ...) 테이블에 이미 저장되어 있다(V41 마이그레이션).

따라서 본 SPEC은 부모 SPEC의 미구현 수락 기준 **AC-NI-005-1 / AC-NI-005-2**를 충족하는 단일 관심사 후속 작업이다.

> 부모 SPEC 원문 AC (재인용):
> - **AC-NI-005-1**: `GET .../posts?bbs=NOTICE&lang=en` → 영어 번역이 있는 항목은 en 제목, 없는 항목은 ko 제목.
> - **AC-NI-005-2**: 목록 응답의 각 항목에 실제 반환된 언어를 식별할 수 있는 필드(예: `language`) 포함.

---

## 2. 요구사항 (Requirements) — EARS 형식

### REQ-NI2-001 (Ubiquitous) — 목록 API `lang` 파라미터 수용

The notice list API (`GET /api/v1/board/posts?bbs=NOTICE`) **shall** accept an optional `lang` query parameter (allowed values `ko`, `en`; default `ko`) and propagate it through the service and mapper layers used to build the list response.

### REQ-NI2-002 (Event-Driven) — 언어별 제목 반환 + 폴백

**When** the list API is called with `lang=en`, **then** for each item the system **shall** return the English translation title from `bbs_post_i18n` (`language='en'`) **if** that translation exists; **if** no English translation exists for an item, **then** the system **shall** return the Korean title from `bbs_post`.

### REQ-NI2-003 (State-Driven) — 항목별 `language` 필드

**While** the list API returns items, the system **shall** include a `language` field on each list item indicating the language actually returned for that item (`en` when the English translation was applied, otherwise `ko`).

---

## 3. 수락 기준 (Acceptance Criteria)

### REQ-NI2-001 (lang 수용)
- **AC-NI2-001-1**: `GET .../posts?bbs=NOTICE&lang=en` 호출 시 `lang`이 서비스(`listPosts`)와 매퍼 쿼리까지 전달된다(컨트롤러가 `lang`을 무시하지 않는다).
- **AC-NI2-001-2**: `lang` 미지정 시 기존 동작과 동일하게 한국어 제목 목록을 반환한다(하위 호환).
- **AC-NI2-001-3**: `lang`이 허용 외 값(예: `ja`)이면 한국어(`ko`)로 폴백하여 처리한다(요청 거부 없이 기본 동작 유지).

### REQ-NI2-002 (제목 번역 + 폴백) — AC-NI-005-1 유래
- **AC-NI2-002-1**: `lang=en` 목록에서 영어 번역이 있는 항목은 `bbs_post_i18n`의 en 제목을 반환한다.
- **AC-NI2-002-2**: `lang=en` 목록에서 영어 번역이 없는 항목은 `bbs_post`의 한국어 제목을 반환한다(항목 단위 폴백).
- **AC-NI2-002-3**: 한 페이지 안에 번역 있는 항목과 없는 항목이 섞여 있어도 각 항목이 독립적으로 올바른 언어 제목을 반환한다.

### REQ-NI2-003 (`language` 필드) — AC-NI-005-2 유래
- **AC-NI2-003-1**: 목록 응답의 각 항목에 `language` 필드가 포함된다.
- **AC-NI2-003-2**: en 제목이 적용된 항목은 `language="en"`, 한국어로 폴백된 항목은 `language="ko"`이다.
- **AC-NI2-003-3**: `lang` 미지정(ko) 호출 시 모든 항목의 `language`가 `"ko"`이다.

### 테스트 스펙 (Test Spec)
- **AC-NI2-TEST-1** (통합): NOTICE 게시판에 (a) en 번역 있는 게시글, (b) en 번역 없는 게시글을 생성한 뒤 `GET .../posts?bbs=NOTICE&lang=en`을 호출 → (a)는 `title=en제목`/`language="en"`, (b)는 `title=ko제목`/`language="ko"`를 검증한다.
- **AC-NI2-TEST-2** (회귀): 동일 데이터에 대해 `lang` 미지정 호출 → 모든 항목 `language="ko"` + 한국어 제목, 기존 페이징 메타(page/size/total)가 변하지 않음을 검증한다.

---

## 4. 기술 접근법 (Technical Approach)

핵심 원칙: **신규 엔드포인트 없음.** 기존 목록 경로(`PostController.listPosts` → `PostServiceImpl.listPosts` → `BbsPostMapper.findByBbsMasterIdPaged`)에 언어 오버레이를 추가한다. 단건 조회가 이미 채택한 "ko 원본 + en 오버레이/폴백" 패턴을 목록에 그대로 확장한다.

### 4.1 매퍼 (MyBatis LEFT JOIN)

- `BbsPostMapper.xml`의 목록 쿼리(`findByBbsMasterIdPaged`, 필요 시 `searchByKeywordPaged`)에 `bbs_post_i18n`을 `LEFT JOIN`한다.
  - 조인 조건: `i18n.post_id = bbs_post.id AND i18n.language = #{lang}` (파라미터화).
  - `lang='ko'`(또는 미지정)일 때는 i18n 조인 결과가 의미 없으므로 한국어 원본 제목을 사용. `lang='en'`일 때 `COALESCE(i18n.title, bbs_post.title)`로 제목을 선택하고, 적용 언어는 `CASE WHEN i18n.title IS NOT NULL THEN 'en' ELSE 'ko' END`로 산출한다.
- 결과 매핑: 목록 행에 번역 제목과 적용 언어가 함께 내려오도록 `resultMap` 또는 결과 타입을 조정한다(기존 `bbsPostResultMap`은 `BbsPost` 엔티티 매핑이므로, 목록 요약 전용 결과 매핑을 사용하거나 서비스단에서 병합).

### 4.2 DTO 변경 (`PostSummary`)

- 목록 응답 DTO `PostSummary`(record)에 `String language` 필드를 추가한다(additive). 기존 필드는 변경하지 않는다.
- 단건 상세 DTO는 본 SPEC 범위 밖(이미 부모 SPEC에서 `Content-Language` 헤더로 처리됨).

### 4.3 서비스/컨트롤러 배선

- `PostService.listPosts(...)` / `searchPosts(...)` 시그니처에 `String lang`을 추가하고, 매퍼에 전달한다.
- `PostController.listPosts(...)`는 이미 받고 있는 `lang`을 서비스로 전달하도록 변경한다(현재 주석으로 막힌 부분 해제).
- `toSummary` 매핑은 번역 제목/적용 언어를 반영하도록 조정(또는 매퍼가 산출한 값을 그대로 사용).

### 4.4 호환/안전

- `lang` 기본값 `ko`로 기존 호출 동작 보존(하위 호환).
- 허용 외 언어는 `ko` 폴백(REQ-NI2-001 AC-001-3).
- 신규 테이블/마이그레이션 없음. `bbs_post_i18n`(V41)을 읽기 전용으로 재사용.

### 4.5 영향 파일 (예상)

- `backend/.../domain/board/controller/PostController.java` (수정: `lang` 서비스 전달)
- `backend/.../domain/board/service/PostService.java` + `PostServiceImpl.java` (수정: `lang` 인자 + 매핑)
- `backend/.../domain/board/dto/PostSummary.java` (수정: `language` 필드 추가)
- `backend/.../resources/mapper/board/BbsPostMapper.xml` (수정: i18n LEFT JOIN + 제목/언어 산출)
- 통합 테스트 (신규: 목록 언어 오버레이/폴백 검증)
- `frontend/admin/src/views/board/PostListView.vue` (이미 EN 배지 표시 — 본 SPEC에서 추가 변경 불필요. 배지는 항목 번역 존재 여부 기준이며 목록 `language` 필드와 독립)

> 주의: 프론트 관리자 목록의 EN 배지는 이미 동작하므로 본 SPEC의 프론트 변경은 비범위에 가깝다. 공개 포털 목록 노출은 아래 비범위 참조.

---

## 5. 비범위 (Out of Scope) — What NOT to Build

- **번역된 항목의 페이징/정렬 변경** — 페이징은 `bbs_post` 기준을 유지한다. en 번역 유무로 항목이 추가/제외되거나 정렬 순서가 바뀌지 않는다(제목만 오버레이).
- **공개 포털(citizen) 목록 API의 다국어** — 본 SPEC은 관리자/공개 공통의 `board/posts` 목록 제목 오버레이까지이며, 시민 프론트엔드의 언어 토글 UI·공개 포털 전용 목록 노출은 별도 관심사(후속 SPEC).
- **본문(content) 목록 반환** — 목록은 제목(`title`)만 언어 오버레이한다. 본문 번역은 단건 조회(부모 SPEC)에서 처리한다.
- **NOTICE 외 게시판 타입의 목록 다국어** — 부모 SPEC과 동일하게 NOTICE에 한정한다.
- **ko/en 외 제3언어** — `bbs_post_i18n.language` CHECK가 ('ko','en')으로 제한되어 있으므로 동일하게 제한한다.
- **검색(`search_vector`)의 다국어 토큰화** — 기존 한국어 검색 동작을 변경하지 않는다.
