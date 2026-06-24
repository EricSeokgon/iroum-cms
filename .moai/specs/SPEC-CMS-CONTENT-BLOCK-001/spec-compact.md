# SPEC-CMS-CONTENT-BLOCK-001 Compact

## Requirements (EARS)

- **REQ-CB-001**: The system SHALL maintain a library of named, reusable content blocks identified by a unique slug in `shared_content_block` table.
- **REQ-CB-002**: WHEN an admin submits a new content block form, the system SHALL validate slug uniqueness, sanitize RICH_TEXT content via Jsoup, and persist the block returning 201 Created.
- **REQ-CB-003**: WHEN an admin requests the block list, the system SHALL return all blocks ordered by updated_at DESC, supporting optional `?status=` and `?type=` filters.
- **REQ-CB-004**: WHEN an admin updates a RICH_TEXT block, the system SHALL sanitize `content_html` via Jsoup before persistence.
- **REQ-CB-005**: IF block_type is 'HTML' and user does NOT have SUPER_ADMIN role, THEN the system SHALL return 403 Forbidden.
- **REQ-CB-006**: WHEN an admin deletes a block, the system SHALL remove it and record action='DELETE' in audit_log.
- **REQ-CB-007**: IF `?status=` is provided, the system SHALL filter blocks to matching status.
- **REQ-CB-008**: WHEN admin toggles status via PATCH /{id}/status, the system SHALL update status and record action='UPDATE' in audit_log.
- **REQ-CB-009**: The system SHALL enforce slug format: `^[a-z0-9]+(-[a-z0-9]+)*$` (max 100 chars).
- **REQ-CB-010**: WHEN admin requests block preview, the system SHALL return sanitized HTML.
- **REQ-CB-011**: IF duplicate slug is submitted, the system SHALL return 409 Conflict with `BLOCK_SLUG_DUPLICATE`.
- **REQ-CB-012**: IF non-existent block id is referenced, the system SHALL return 404 Not Found with `BLOCK_NOT_FOUND`.
- **REQ-CB-013**: IF `?type=` is provided, the system SHALL filter blocks to matching block_type.
- **REQ-CB-014**: WHERE block_type='EMBED' and content_raw contains an embed URL, the system SHALL validate the provider domain against the allowlist (youtube.com, vimeo.com, map.kakao.com) before persisting.
- **REQ-CB-015**: IF the EMBED provider domain is not in the allowlist, THEN the system SHALL return 422 Unprocessable Entity with error code `BLOCK_EMBED_PROVIDER_INVALID`.
- **REQ-CB-016**: WHEN an admin creates or updates a block of type MARKDOWN, the system SHALL sanitize `content_raw` via Jsoup text-only filter before persistence.

## Acceptance Criteria

- **AC-001**: POST /api/v1/content/blocks (RICH_TEXT) → 201 Created, id in response, 1 row in shared_content_block, 1 row in audit_log with action='CREATE'
- **AC-002**: POST with duplicate slug → 409 Conflict, error code BLOCK_SLUG_DUPLICATE, no DB insert
- **AC-003**: GET /api/v1/content/blocks?status=ACTIVE with 3 ACTIVE + 1 INACTIVE → 200 OK, 3 items returned
- **AC-004**: POST with blockType='HTML' by non-SUPER_ADMIN → 403 Forbidden, no DB insert
- **AC-005**: PUT RICH_TEXT block with XSS payload → 200 OK, script tag removed from DB content_html
- **AC-006**: DELETE /api/v1/content/blocks/{id} → 204 No Content, row deleted, audit_log action='DELETE'
- **AC-007**: PATCH /{id}/status → 200 OK, status updated, audit_log action='UPDATE'
- **AC-008**: GET /api/v1/content/blocks/99999 → 404 Not Found, error code BLOCK_NOT_FOUND
- **AC-009**: POST with invalid slug format → 400 Bad Request
- **AC-010**: GET /api/v1/content/blocks?type=RICH_TEXT → 200, only RICH_TEXT blocks returned
- **AC-011**: GET /api/v1/content/blocks/1/preview → 200, sanitized HTML returned
- **AC-012**: POST EMBED blockType + tiktok.com URL → 422, error code BLOCK_EMBED_PROVIDER_INVALID
- **AC-013**: PUT MARKDOWN block with XSS → 200, script tag removed from content_raw
- **AC-014**: POST EMBED blockType + youtube.com URL → 201 Created

## Files to Modify

### New (Backend)
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

### New (Frontend)
- `frontend/admin/src/views/content/ContentBlockManagerView.vue`

### Modified (Frontend)
- `frontend/admin/src/api/content.ts`
- `frontend/admin/src/router/index.ts`

## Exclusions

1. 페이지-블록 자동 삽입 연동 (별도 SPEC)
2. IMAGE 타입 블록 (미디어 시스템 사용)
3. 블록 버전 히스토리 (별도 SPEC)
4. 블록 카테고리/태그
5. 포털 프론트엔드 렌더링 API
