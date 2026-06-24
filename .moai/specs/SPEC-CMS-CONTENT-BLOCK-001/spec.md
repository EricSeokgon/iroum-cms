---
id: SPEC-CMS-CONTENT-BLOCK-001
version: "0.1.0"
status: Draft
created_at: "2026-06-24"
updated_at: "2026-06-24"
author: ircp
priority: Medium
labels: ["content", "cms", "block", "reusable"]
issue_number: 0
---

## HISTORY

| 버전 | 날짜 | 작성자 | 변경 내용 |
|------|------|--------|-----------|
| 0.1.0 | 2026-06-24 | ircp | 최초 작성 |

---

## 1. 개요

### 1.1 목적

관리자가 재사용 가능한 콘텐츠 블록(HTML/텍스트 조각)을 라이브러리 형태로 CRUD 관리할 수 있는 기능을 제공한다. 블록에는 고유한 slug가 부여되어 페이지 편집기나 템플릿에서 참조하여 여러 페이지에서 동일한 콘텐츠를 재사용할 수 있다.

**주요 사용 사례**: 정부 CMS의 공통 안내 박스, 서비스 소개 섹션, 긴급 공지 배너, 개인정보 처리 안내문 등 반복 등장하는 콘텐츠 컴포넌트 관리.

### 1.2 범위

- 재사용 가능한 콘텐츠 블록 CRUD (생성·조회·수정·삭제)
- 블록 타입: RICH_TEXT, HTML, MARKDOWN, EMBED
- 블록 상태 토글 (ACTIVE ↔ INACTIVE)
- 관리자 UI (목록·폼)
- 감사 로그(audit_log) 연동

### 1.3 배경

기존 `content_block` 테이블(V13 마이그레이션)은 `page_id NOT NULL` FK 구조로 특정 페이지에 종속된다. 재사용 가능한 공유 블록은 **별도의 `shared_content_block` 테이블(V45 마이그레이션)**로 구현한다.

---

## 2. EARS 형식 요구사항

### 2.1 항상 적용 (Ubiquitous)

**REQ-CB-001**: The system SHALL maintain a library of named, reusable content blocks identified by a unique slug in `shared_content_block` table.

**REQ-CB-009**: The system SHALL enforce slug format as lowercase alphanumeric with hyphens only (regex: `^[a-z0-9]+(-[a-z0-9]+)*$`, max 100 chars).

### 2.2 이벤트 기반 (Event-driven)

**REQ-CB-002**: WHEN an admin submits a new content block form with valid fields, the system SHALL validate slug uniqueness, sanitize RICH_TEXT content via Jsoup, and persist the block returning 201 Created with the generated id.

**REQ-CB-003**: WHEN an admin requests the block list (`GET /api/v1/content/blocks`), the system SHALL return all blocks ordered by updated_at DESC, supporting optional `?status=` and `?type=` query filters.

**REQ-CB-004**: WHEN an admin updates a block of type RICH_TEXT, the system SHALL sanitize `content_html` via Jsoup whitelist before persistence to prevent XSS.

**REQ-CB-006**: WHEN an admin deletes a block, the system SHALL verify the block exists, remove it from `shared_content_block`, and record an entry in `audit_log` with action='DELETE', entity_type='shared_content_block'.

**REQ-CB-008**: WHEN an admin toggles block status via `PATCH /{id}/status`, the system SHALL update the `status` field and record in `audit_log` with action='UPDATE'.

**REQ-CB-010**: WHEN an admin requests a block preview (`GET /{id}/preview`), the system SHALL return rendered, sanitized HTML safe for display in an iframe or div.

### 2.3 원치 않는 동작 (Unwanted behavior)

**REQ-CB-005**: IF block_type is 'HTML' and the requesting user does NOT have SUPER_ADMIN role, THEN the system SHALL reject the request with 403 Forbidden. (HTML 블록은 Jsoup 정제를 우회하므로 SUPER_ADMIN 전용)

**REQ-CB-011**: IF a duplicate slug is submitted, THEN the system SHALL return 409 Conflict with error code `BLOCK_SLUG_DUPLICATE`.

**REQ-CB-012**: IF a non-existent block id is referenced, THEN the system SHALL return 404 Not Found with error code `BLOCK_NOT_FOUND`.

### 2.4 선택적 (Option)

**REQ-CB-007**: WHERE the `?status=` query parameter is provided, the system SHALL filter blocks to only return blocks matching that status value.

**REQ-CB-013**: WHERE the `?type=` query parameter is provided, the system SHALL filter blocks to only return blocks matching that block_type value.

### 2.5 복합 (Complex)

**REQ-CB-014**: WHERE block_type='EMBED' and content_raw contains an embed URL, the system SHALL validate the provider domain against the allowlist (youtube.com, vimeo.com, map.kakao.com) before persisting.

**REQ-CB-015**: IF the EMBED provider domain is not in the allowlist, THEN the system SHALL return 422 Unprocessable Entity with error code `BLOCK_EMBED_PROVIDER_INVALID`.

**REQ-CB-016**: WHEN an admin creates or updates a block of type MARKDOWN, the system SHALL sanitize `content_raw` via Jsoup text-only filter before rendering to prevent injection attacks.

---

## 3. 기술 접근 방식

### 3.1 아키텍처

기존 Banner/Popup/Template 도메인과 동일한 레이어드 아키텍처를 따른다:

```
ContentBlockController → SharedContentBlockService (interface/impl)
                       → SharedContentBlockMapper (MyBatis)
                       → SharedContentBlockMapper.xml
```

### 3.2 DB 스키마 (V45 마이그레이션)

```sql
CREATE TABLE shared_content_block (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    slug         VARCHAR(100) NOT NULL UNIQUE,
    block_type   VARCHAR(20)  NOT NULL,
    content_html TEXT,
    content_raw  TEXT,
    description  VARCHAR(500),
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by   BIGINT       REFERENCES admin_user(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_scb_type   CHECK (block_type IN ('RICH_TEXT','HTML','MARKDOWN','EMBED')),
    CONSTRAINT chk_scb_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_scb_slug   CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
CREATE INDEX idx_scb_status ON shared_content_block(status);
CREATE INDEX idx_scb_type   ON shared_content_block(block_type);
```

### 3.3 권한 체계

| 작업 | 권한 |
|------|------|
| 목록·상세 조회 | `hasAuthority('CONTENT:READ')` |
| 생성·수정·삭제·상태변경 | `hasAuthority('CONTENT:WRITE')` |
| HTML 타입 블록 생성·수정 | `hasAuthority('CONTENT:WRITE') AND hasRole('SUPER_ADMIN')` |

### 3.4 감사 로그

- action 값: `CREATE`, `UPDATE`, `DELETE` 만 사용 (체크 제약 준수)
- entity_type: `'shared_content_block'`
- entityId: `String.valueOf(id)`

---

## 4. 수정 대상 파일

### 신규 생성 (Backend)
- `backend/src/main/resources/db/migration/V45__shared_content_block.sql`
- `backend/src/main/java/kr/co/ircp/cms/domain/content/block/entity/SharedContentBlock.java`
- `backend/src/main/java/kr/co/ircp/cms/domain/content/block/dto/ContentBlockRequest.java`
- `backend/src/main/java/kr/co/ircp/cms/domain/content/block/dto/ContentBlockResponse.java`
- `backend/src/main/java/kr/co/ircp/cms/domain/content/block/mapper/SharedContentBlockMapper.java`
- `backend/src/main/resources/mapper/content/SharedContentBlockMapper.xml`
- `backend/src/main/java/kr/co/ircp/cms/domain/content/block/service/SharedContentBlockService.java`
- `backend/src/main/java/kr/co/ircp/cms/domain/content/block/service/SharedContentBlockServiceImpl.java`
- `backend/src/main/java/kr/co/ircp/cms/domain/content/block/controller/ContentBlockController.java`
- `backend/src/main/java/kr/co/ircp/cms/domain/content/block/exception/ContentBlockNotFoundException.java`
- `backend/src/main/java/kr/co/ircp/cms/domain/content/block/exception/ContentBlockSlugDuplicateException.java`
- `backend/src/test/java/kr/co/ircp/cms/domain/content/block/ContentBlockIT.java`

### 신규 생성 (Frontend)
- `frontend/admin/src/views/content/ContentBlockManagerView.vue`

### 수정 (Frontend)
- `frontend/admin/src/api/content.ts` — SharedContentBlock 타입·API 함수 추가
- `frontend/admin/src/router/index.ts` — `/content/blocks` 라우트 추가

---

## 5. 제외 범위 (What NOT to Build)

1. **페이지-블록 링크 기능** 제외 — 공유 블록을 페이지에 자동 삽입하는 연동은 별도 SPEC으로 분리
2. **IMAGE 타입 블록** 제외 — 이미지는 미디어 관리 시스템(Media domain)에서 별도 처리
3. **블록 버전 히스토리** 제외 — 변경 이력 추적은 별도 SPEC으로 분리
4. **블록 카테고리/태그** 제외 — 1차 MVP에서는 type/status 필터만 지원
5. **공개 렌더링 API** 제외 — 프론트엔드 포털에서의 블록 렌더링은 별도 SPEC
