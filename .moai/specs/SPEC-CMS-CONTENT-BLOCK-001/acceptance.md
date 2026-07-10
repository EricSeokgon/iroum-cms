# SPEC-CMS-CONTENT-BLOCK-001 인수 조건

## AC-001: 블록 생성 성공 (RICH_TEXT) — REQ-CB-001, REQ-CB-002

**Given**: CONTENT:WRITE 권한을 가진 관리자가 로그인된 상태  
**When**: POST /api/v1/content/blocks 요청:
```json
{
  "name": "공통 안내 박스",
  "slug": "common-notice-box",
  "blockType": "RICH_TEXT",
  "contentHtml": "<p>서비스 이용 안내입니다.</p>",
  "description": "공통으로 사용하는 안내 문구"
}
```
**Then**:
- HTTP 201 Created 반환
- 응답 body에 `id` 포함
- `shared_content_block` 테이블에 1건 삽입됨
- `audit_log`에 action='CREATE', entity_type='shared_content_block' 1건 삽입됨

---

## AC-002: 슬러그 중복 시 409 Conflict — REQ-CB-011

**Given**: slug='common-notice-box' 블록이 이미 존재  
**When**: POST /api/v1/content/blocks with `"slug": "common-notice-box"`  
**Then**:
- HTTP 409 Conflict 반환
- 응답 body에 error code `BLOCK_SLUG_DUPLICATE` 포함
- `shared_content_block` 테이블에 중복 삽입 없음

---

## AC-003: 블록 목록 조회 — 상태 필터 — REQ-CB-003, REQ-CB-007

**Given**: ACTIVE 블록 3건, INACTIVE 블록 1건이 DB에 존재  
**When**: GET /api/v1/content/blocks?status=ACTIVE (CONTENT:READ 권한 보유)  
**Then**:
- HTTP 200 OK 반환
- 응답 배열 크기 = 3 (INACTIVE 블록 제외)

---

## AC-004: HTML 타입 블록 — SUPER_ADMIN이 아닌 관리자 거부 — REQ-CB-005

**Given**: CONTENT:WRITE 권한은 있으나 SUPER_ADMIN 역할이 아닌 관리자  
**When**: POST /api/v1/content/blocks with `"blockType": "HTML"`  
**Then**:
- HTTP 403 Forbidden 반환
- `shared_content_block` 테이블에 삽입 없음

---

## AC-005: RICH_TEXT 수정 시 Jsoup XSS 정제 — REQ-CB-004

**Given**: id=1인 RICH_TEXT 블록 존재  
**When**: PUT /api/v1/content/blocks/1 요청:
```json
{
  "name": "수정된 안내 박스",
  "slug": "common-notice-box",
  "blockType": "RICH_TEXT",
  "contentHtml": "<p>안내<script>alert('xss')</script></p>"
}
```
**Then**:
- HTTP 200 OK 반환
- DB `content_html` 값에서 `<script>` 태그가 제거됨 (`<p>안내</p>` 형태로 저장)

---

## AC-006: 블록 삭제 후 audit_log 기록 — REQ-CB-006

**Given**: id=1인 블록이 존재, CONTENT:WRITE 권한 보유  
**When**: DELETE /api/v1/content/blocks/1  
**Then**:
- HTTP 204 No Content 반환
- `shared_content_block` 테이블에서 id=1 레코드 삭제됨
- `audit_log` 테이블에 action='DELETE', entity_type='shared_content_block', entity_id='1' 1건 삽입됨

---

## AC-007: 블록 상태 토글 — REQ-CB-008

**Given**: id=1인 ACTIVE 블록 존재, CONTENT:WRITE 권한 보유  
**When**: PATCH /api/v1/content/blocks/1/status with `{"status": "INACTIVE"}`  
**Then**:
- HTTP 200 OK 반환
- DB `status` = 'INACTIVE'로 변경됨
- `audit_log`에 action='UPDATE' 1건 삽입됨

---

## AC-008: 존재하지 않는 블록 조회 시 404 — REQ-CB-012

**Given**: id=99999 블록은 존재하지 않음  
**When**: GET /api/v1/content/blocks/99999  
**Then**:
- HTTP 404 Not Found 반환
- 응답 body에 error code `BLOCK_NOT_FOUND` 포함

---

## AC-009: 잘못된 슬러그 형식 거부 — REQ-CB-009

**Given**: CONTENT:WRITE 권한을 가진 관리자  
**When**: POST /api/v1/content/blocks with `"slug": "INVALID Slug 한글!"`  
**Then**:
- HTTP 400 Bad Request 반환
- 응답 body에 슬러그 형식 오류 메시지 포함 (`^[a-z0-9]+(-[a-z0-9]+)*$` 형식 위반)
- `shared_content_block` 테이블에 삽입 없음

---

## AC-010: 블록 타입 필터 조회 — REQ-CB-013

**Given**: RICH_TEXT 블록 2건, MARKDOWN 블록 1건, EMBED 블록 1건이 DB에 존재  
**When**: GET /api/v1/content/blocks?type=RICH_TEXT (CONTENT:READ 권한 보유)  
**Then**:
- HTTP 200 OK 반환
- 응답 배열 크기 = 2 (MARKDOWN, EMBED 블록 제외)

---

## AC-011: 블록 미리보기 — REQ-CB-010

**Given**: id=1인 RICH_TEXT 블록이 존재, CONTENT:READ 권한 보유  
**When**: GET /api/v1/content/blocks/1/preview  
**Then**:
- HTTP 200 OK 반환
- 응답 body에 sanitized HTML 포함 (script 태그 제거 확인)
- Content-Type: text/html 또는 application/json (renderHtml 필드)

---

## AC-012: EMBED 타입 — 유효하지 않은 프로바이더 거부 — REQ-CB-015

**Given**: CONTENT:WRITE 권한을 가진 관리자  
**When**: POST /api/v1/content/blocks with:
```json
{
  "name": "외부 동영상",
  "slug": "external-video",
  "blockType": "EMBED",
  "contentRaw": "https://tiktok.com/video/12345"
}
```
**Then**:
- HTTP 422 Unprocessable Entity 반환
- 응답 body에 error code `BLOCK_EMBED_PROVIDER_INVALID` 포함
- `shared_content_block` 테이블에 삽입 없음

---

## AC-013: MARKDOWN 타입 — Jsoup 정제 — REQ-CB-016

**Given**: id=1인 MARKDOWN 블록 존재  
**When**: PUT /api/v1/content/blocks/1 요청:
```json
{
  "name": "마크다운 블록",
  "slug": "md-block",
  "blockType": "MARKDOWN",
  "contentRaw": "안내문\n<script>alert('xss')</script>"
}
```
**Then**:
- HTTP 200 OK 반환
- DB `content_raw` 값에서 `<script>` 태그가 제거됨 (text-only 정제)

---

## AC-014: EMBED 타입 — 허용된 프로바이더 승인 — REQ-CB-014

**Given**: CONTENT:WRITE 권한을 가진 관리자  
**When**: POST /api/v1/content/blocks with:
```json
{
  "name": "유튜브 동영상",
  "slug": "youtube-intro",
  "blockType": "EMBED",
  "contentRaw": "https://youtube.com/watch?v=abc123"
}
```
**Then**:
- HTTP 201 Created 반환
- `shared_content_block` 테이블에 1건 삽입됨

---

## 품질 게이트

- 단위 테스트 커버리지: Service 계층 85% 이상
- 통합 테스트: Docker 환경 (`eclipse-temurin:17-jdk-jammy`)에서 AC-001 ~ AC-014 모두 통과
- `@PreAuthorize` 권한 검사: 모든 write 엔드포인트에 적용 확인
- audit_log action 값: 반드시 CREATE / UPDATE / DELETE 중 하나만 사용
