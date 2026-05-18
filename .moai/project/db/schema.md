---
engine: postgresql
orm: Spring Data JPA / MyBatis (egovFrame)
last_synced_at: 2026-05-18
manifest_hash: v33
---

# Database Schema

> Engine: PostgreSQL 16 | ORM: Spring Data JPA / MyBatis | Migrations: Flyway | Last sync: 2026-05-18 (V33)
>
> Extensions: `pgcrypto` (UUID, 암호화 해시), `pg_trgm` (한국어 LIKE 검색 GIN 인덱스), `vector` (pgvector 384차원 임베딩 — V33)
>
> Timezone: UTC 저장, 애플리케이션에서 Asia/Seoul 변환

---

## Tables

### Auth 도메인

---

### `users`

> Added: V2__auth_schema.sql | Modified: V5 (organization_id), V24 (PII email columns), V26 (DROP email) | Domain: Auth

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| uuid | UUID | NO | gen_random_uuid() | 외부 노출용 식별자 |
| username | VARCHAR(50) | NO | | UNIQUE |
| password_hash | VARCHAR(72) | NO | | BCrypt strength=12 |
| name | VARCHAR(100) | NO | | |
| status | VARCHAR(20) | NO | 'ACTIVE' | ACTIVE/INACTIVE/LOCKED/DELETED |
| fail_count | INT | NO | 0 | 연속 로그인 실패 횟수 |
| locked_until | TIMESTAMPTZ | YES | | 잠금 해제 시각 |
| last_login_at | TIMESTAMPTZ | YES | | |
| password_changed_at | TIMESTAMPTZ | NO | NOW() | |
| organization_id | BIGINT | YES | | FK → organization(id) (V5 추가) |
| email_encrypted | BYTEA | YES | | AES-256-GCM 암호문 (V24 추가) |
| email_iv | BYTEA | YES | | GCM IV 12 bytes (V24 추가) |
| email_tag | BYTEA | YES | | GCM auth tag 16 bytes (V24 추가) |
| email_hmac | VARCHAR(64) | YES | | HMAC-SHA256 lookup 키 (V24 추가) |
| email_key_version | SMALLINT | NO | 1 | PII 키 버전 (V24 추가) |
| created_at | TIMESTAMPTZ | NO | NOW() | |
| updated_at | TIMESTAMPTZ | NO | NOW() | |
| deleted_at | TIMESTAMPTZ | YES | | soft delete |

> **PII**: `email` 평문 컬럼은 V26에서 DROP. email은 `email_encrypted`(AES-256-GCM) + `email_hmac`(HMAC-SHA256 lookup) 경로만 사용.

**Indexes:**
- `idx_users_status` ON (status) WHERE deleted_at IS NULL
- `idx_users_locked_until` ON (locked_until) WHERE locked_until IS NOT NULL
- `idx_users_organization` ON (organization_id) WHERE deleted_at IS NULL
- `idx_users_email_hmac` UNIQUE ON (email_hmac) WHERE email_hmac IS NOT NULL
- `idx_users_username_trgm` GIN (username gin_trgm_ops) — V23
- `idx_users_name_trgm` GIN (name gin_trgm_ops) — V23

**Foreign Keys:** organization_id → organization(id)

---

### `roles`

> Added: V2__auth_schema.sql | Domain: Auth

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| code | VARCHAR(50) | NO | | PK |
| name | VARCHAR(100) | NO | | |
| description | TEXT | YES | | |
| is_system | BOOLEAN | NO | FALSE | 시스템 역할 — 삭제 금지 |
| aliased_to | VARCHAR(50) | YES | | FK → roles(code); NULL=실제, NOT NULL=alias |
| created_at | TIMESTAMPTZ | NO | NOW() | |

> **시드**: SUPER_ADMIN, SYSADMIN(alias→SUPER_ADMIN), DEPT_ADMIN, EDITOR, VIEWER

**Indexes:** `idx_roles_aliased_to` ON (aliased_to) WHERE aliased_to IS NOT NULL

---

### `user_roles`

> Added: V2__auth_schema.sql | Domain: Auth

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| user_id | BIGINT | NO | | PK, FK → users(id) ON DELETE CASCADE |
| role_code | VARCHAR(50) | NO | | PK, FK → roles(code) ON DELETE RESTRICT |
| granted_at | TIMESTAMPTZ | NO | NOW() | |
| granted_by | BIGINT | YES | | FK → users(id) |

**Indexes:** `idx_user_roles_role` ON (role_code)

---

### `password_history`

> Added: V2__auth_schema.sql | Domain: Auth

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| user_id | BIGINT | NO | | FK → users(id) ON DELETE CASCADE |
| password_hash | VARCHAR(72) | NO | | |
| changed_at | TIMESTAMPTZ | NO | NOW() | |

**Indexes:** `idx_password_history_user` ON (user_id, changed_at DESC)

---

### `login_history`

> Added: V2__auth_schema.sql | Domain: Auth

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| user_id | BIGINT | YES | | FK → users(id); NULL = 미존재 사용자 실패 기록 |
| username | VARCHAR(50) | YES | | |
| ip_address | VARCHAR(45) | YES | | |
| user_agent | TEXT | YES | | |
| success | BOOLEAN | NO | | |
| failure_reason | VARCHAR(50) | YES | | |
| created_at | TIMESTAMPTZ | NO | NOW() | |

**Indexes:** `idx_login_history_user` ON (user_id, created_at DESC), `idx_login_history_username` ON (username, created_at DESC)

---

### `refresh_tokens`

> Added: V2__auth_schema.sql | Domain: Auth

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| token_hash | VARCHAR(64) | NO | | UNIQUE — SHA-256(Refresh JWT) |
| user_id | BIGINT | NO | | FK → users(id) ON DELETE CASCADE |
| expires_at | TIMESTAMPTZ | NO | | |
| revoked_at | TIMESTAMPTZ | YES | | |
| ip_address | VARCHAR(45) | YES | | |
| user_agent | TEXT | YES | | |
| created_at | TIMESTAMPTZ | NO | NOW() | |

**Indexes:** `idx_refresh_tokens_user_active` ON (user_id) WHERE revoked_at IS NULL, `idx_refresh_tokens_expires` ON (expires_at) WHERE revoked_at IS NULL

---

### `token_blacklist`

> Added: V2__auth_schema.sql | Domain: Auth

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| token_hash | VARCHAR(64) | NO | | PK — SHA-256(Access JWT) |
| revoked_at | TIMESTAMPTZ | NO | NOW() | |
| expires_at | TIMESTAMPTZ | NO | | GC 기준 |

**Indexes:** `idx_token_blacklist_expires` ON (expires_at)

---

### `verification_request`

> Added: V8__verification_schema.sql | Domain: Auth

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| request_id | UUID | NO | gen_random_uuid() | UNIQUE |
| channel | VARCHAR(20) | NO | | EMAIL (SMS는 v0.4+) |
| target | VARCHAR(255) | NO | | |
| purpose | VARCHAR(50) | NO | | SIGNUP/PASSWORD_RESET/IMPORTANT_CHANGE |
| code_hash | VARCHAR(72) | NO | | BCrypt(12) |
| created_at | TIMESTAMPTZ | NO | NOW() | |
| expires_at | TIMESTAMPTZ | NO | | |
| attempts | INT | NO | 0 | |
| max_attempts | INT | NO | 3 | |
| status | VARCHAR(20) | NO | 'PENDING' | PENDING/VERIFIED/EXPIRED/FAILED |
| verified_at | TIMESTAMPTZ | YES | | |
| verified_token | VARCHAR(64) | YES | | UNIQUE — 5분 유효 |
| requester_ip_hash | VARCHAR(64) | YES | | |
| user_agent | TEXT | YES | | |

**Indexes:** `idx_vreq_target`, `idx_vreq_status_expires`, `idx_vreq_verified_token`

---

### `verification_history`

> Added: V8__verification_schema.sql | Domain: Auth

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| target | VARCHAR(255) | NO | | |
| channel | VARCHAR(20) | NO | | |
| purpose | VARCHAR(50) | NO | | |
| success | BOOLEAN | NO | | |
| failure_reason | VARCHAR(100) | YES | | |
| requester_ip_hash | VARCHAR(64) | YES | | |
| user_agent | TEXT | YES | | |
| occurred_at | TIMESTAMPTZ | NO | NOW() | |

**Indexes:** `idx_vhist_target`, `idx_vhist_ip_recent`

---

### Organization 도메인

---

### `organization`

> Added: V5__organization_schema.sql | Domain: Organization

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| code | VARCHAR(50) | NO | | UNIQUE |
| name | VARCHAR(200) | NO | | |
| description | TEXT | YES | | |
| parent_id | BIGINT | YES | | FK → organization(id) ON DELETE RESTRICT |
| depth | INT | NO | 0 | 루트=0, 최대 5 (CHECK 제약) |
| path | TEXT | NO | | materialized path: /1/3/7/ |
| sort_order | INT | NO | 0 | |
| status | VARCHAR(20) | NO | 'ACTIVE' | ACTIVE/INACTIVE/DELETED |
| created_at | TIMESTAMPTZ | NO | NOW() | |
| updated_at | TIMESTAMPTZ | NO | NOW() | |
| deleted_at | TIMESTAMPTZ | YES | | |

> **시드**: id=1, code='ROOT', name='본부'

**Indexes:** `idx_org_parent`, `idx_org_status_sort`, `idx_org_path_trgm` GIN

---

### `organization_history`

> Added: V5__organization_schema.sql | Domain: Organization

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| org_id | BIGINT | NO | | FK → organization(id) ON DELETE CASCADE |
| version | INT | NO | | 단조 증가 버전 번호 (UNIQUE with org_id) |
| snapshot | JSONB | NO | | 변경 시점 전체 스냅샷 |
| changed_by | BIGINT | YES | | FK → users(id) |
| changed_at | TIMESTAMPTZ | NO | NOW() | |
| change_summary | TEXT | YES | | |

---

### Permissions 도메인

---

### `permissions`

> Added: V6__permissions_schema.sql | Domain: Permissions

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| code | VARCHAR(100) | NO | | PK — RESOURCE:ACTION 형식 |
| resource | VARCHAR(50) | NO | | |
| action | VARCHAR(20) | NO | | READ/WRITE/DELETE/EXECUTE/ADMIN |
| description | TEXT | YES | | |
| created_at | TIMESTAMPTZ | NO | NOW() | |

> **시드**: USER:*, ORGANIZATION:*, ROLE:*, PERMISSION:*, AUDIT:READ, SYSTEM:ADMIN + Bundle C/D 권한 다수

---

### `role_permissions`

> Added: V6__permissions_schema.sql | Domain: Permissions

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| role_code | VARCHAR(50) | NO | | PK, FK → roles(code) ON DELETE CASCADE |
| permission_code | VARCHAR(100) | NO | | PK, FK → permissions(code) ON DELETE CASCADE |
| granted_at | TIMESTAMPTZ | NO | NOW() | |
| granted_by | BIGINT | YES | | FK → users(id) |

**Indexes:** `idx_role_permissions_role`, `idx_role_permissions_perm`

---

### `permission_change_history`

> Added: V7__permission_change_history.sql | Domain: Permissions | **APPEND-ONLY**

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| change_type | VARCHAR(40) | NO | | ROLE_ASSIGN/ROLE_UNASSIGN/ROLE_PERMISSION_GRANT/ROLE_PERMISSION_REVOKE |
| target_user_id | BIGINT | YES | | FK → users(id) |
| target_role_code | VARCHAR(50) | YES | | FK → roles(code) |
| target_resource | VARCHAR(100) | NO | | 역할 코드 또는 권한 코드 |
| changed_by | BIGINT | YES | | FK → users(id) |
| changed_at | TIMESTAMPTZ | NO | NOW() | |
| severity | VARCHAR(20) | NO | 'INFO' | INFO/WARN/CRITICAL |
| reason | TEXT | YES | | |
| actor_ip | VARCHAR(45) | YES | | |
| trace_id | VARCHAR(64) | YES | | |

> UPDATE/DELETE 트리거로 차단 (REQ-AUTH-016)

---

### Audit / System Log 도메인

---

### `audit_log`

> Added: V3__audit_log.sql | Domain: System | **APPEND-ONLY**

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| event_time | TIMESTAMPTZ | NO | NOW() | |
| actor_id | BIGINT | YES | | FK → users(id) |
| actor_role | VARCHAR(50) | YES | | |
| action | VARCHAR(50) | NO | | CREATE/READ/UPDATE/DELETE/LOGIN/LOGIN_FAILURE/LOGOUT/PERMISSION_CHANGE/PERMISSION_DENIED/PASSWORD_CHANGE/PASSWORD_RESET/TOKEN_REFRESH/TOKEN_REVOKE/EXPORT/BATCH |
| entity_type | VARCHAR(100) | YES | | |
| entity_id | VARCHAR(100) | YES | | |
| before_value | JSONB | YES | | |
| after_value | JSONB | YES | | |
| ip_address | VARCHAR(45) | YES | | |
| user_agent | TEXT | YES | | |
| trace_id | VARCHAR(64) | YES | | |
| severity | VARCHAR(20) | NO | 'INFO' | INFO/WARN/CRITICAL |
| result | VARCHAR(20) | NO | 'SUCCESS' | SUCCESS/FAILURE |
| failure_reason | TEXT | YES | | |
| duration_ms | INT | YES | | |

> UPDATE/DELETE 트리거로 차단 (SPEC-CMS-005 §7.4). 보존정책: 60개월 ARCHIVE.

**Indexes:** `idx_audit_log_event_time`, `idx_audit_log_actor`, `idx_audit_log_critical` (severity='CRITICAL'), `idx_audit_log_action_time`

---

### `personal_data_access_log`

> Added: V9__personal_data_access_log.sql | Domain: PII/Security | **APPEND-ONLY**

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| viewer_id | BIGINT | NO | | FK → users(id) |
| viewer_role | VARCHAR(50) | YES | | |
| target_user_id | BIGINT | NO | | FK → users(id) |
| accessed_fields | JSONB | NO | | 조회된 개인정보 필드 목록 (배열) |
| purpose | VARCHAR(50) | NO | | BUSINESS_INQUIRY/SUPPORT/AUDIT/SELF_VIEW/ADMIN_USER_LIST/ADMIN_USER_EDIT/EXPORT |
| ip_address | VARCHAR(45) | YES | | |
| user_agent | TEXT | YES | | |
| trace_id | VARCHAR(64) | YES | | |
| accessed_at | TIMESTAMPTZ | NO | NOW() | |

> UPDATE/DELETE 트리거로 차단 (개인정보보호법 §29). 보존정책: 6개월 ARCHIVE.

**Indexes:** `idx_pda_target`, `idx_pda_viewer`, `idx_pda_time`, `idx_pda_purpose`

### `personal_data_access_log_archive`

> Added: V9 | Domain: PII/Security — 6개월 콜드 이관 보관 테이블 (personal_data_access_log와 동일 구조)

---

### Board 도메인

---

### `bbs_master`

> Added: V10__board_schema.sql | Modified: V27 (deleted_at) | Domain: Board

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| code | VARCHAR(50) | NO | | UNIQUE |
| name | VARCHAR(200) | NO | | |
| description | TEXT | YES | | |
| type | VARCHAR(20) | NO | | NORMAL/NOTICE/QNA/FAQ/GALLERY/PUBLICATION/SURVEY |
| use_comment | BOOLEAN | NO | TRUE | |
| use_attachment | BOOLEAN | NO | TRUE | |
| max_attachment_count | INT | NO | 5 | |
| max_attachment_size_kb | INT | NO | 10240 | |
| allow_anonymous | BOOLEAN | NO | FALSE | |
| allow_secret | BOOLEAN | NO | FALSE | Q&A 비공개 허용 |
| page_size | INT | NO | 20 | |
| role_required_read | VARCHAR(50) | YES | | |
| role_required_write | VARCHAR(50) | YES | | |
| status | VARCHAR(20) | NO | 'ACTIVE' | ACTIVE/INACTIVE |
| metadata | JSONB | YES | | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| deleted_at | TIMESTAMPTZ | YES | | soft delete (V27 추가) |

**Indexes:** `idx_bbs_master_status`, `idx_bbs_master_type` WHERE status='ACTIVE', `idx_bbs_master_active` ON (status) WHERE deleted_at IS NULL (V27)

---

### `bbs_post`

> Added: V10__board_schema.sql | Domain: Board

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| bbs_id | BIGINT | NO | | FK → bbs_master(id) ON DELETE RESTRICT |
| title | VARCHAR(500) | NO | | |
| content_html | TEXT | NO | | |
| content_text | TEXT | NO | | |
| search_vector | TSVECTOR | YES | | 트리거로 자동 갱신 |
| category_code | VARCHAR(50) | YES | | |
| author_id | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| author_name | VARCHAR(100) | YES | | |
| is_notice | BOOLEAN | NO | FALSE | |
| notice_from | TIMESTAMPTZ | YES | | |
| notice_until | TIMESTAMPTZ | YES | | |
| is_secret | BOOLEAN | NO | FALSE | |
| view_count | BIGINT | NO | 0 | |
| like_count | BIGINT | NO | 0 | |
| comment_count | INT | NO | 0 | |
| attachment_count | INT | NO | 0 | |
| status | VARCHAR(20) | NO | 'PUBLISHED' | DRAFT/PUBLISHED/HIDDEN/DELETED |
| published_at | TIMESTAMPTZ | YES | | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| deleted_at | TIMESTAMPTZ | YES | | |

**Indexes:** `idx_bbs_post_active`, `idx_bbs_post_notice_active`, `idx_bbs_post_author`, `idx_bbs_post_category`, `idx_bbs_post_search_vector` GIN, `idx_bbs_post_title_trgm` GIN

---

### `bbs_comment`

> Added: V10__board_schema.sql | Domain: Board

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| post_id | BIGINT | NO | | FK → bbs_post(id) ON DELETE CASCADE |
| parent_comment_id | BIGINT | YES | | FK → bbs_comment(id) — 최대 1단계 트리거 |
| author_id | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| anonymous_name | VARCHAR(100) | YES | | |
| anonymous_pwd_hash | VARCHAR(60) | YES | | |
| content | TEXT | NO | | |
| ip_address | INET | YES | | |
| status | VARCHAR(20) | NO | 'VISIBLE' | VISIBLE/HIDDEN/DELETED |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| deleted_at | TIMESTAMPTZ | YES | | |

**Indexes:** `idx_bbs_comment_post`, `idx_bbs_comment_parent`, `idx_bbs_comment_author`

---

### `bbs_attachment`

> Added: V10__board_schema.sql | Domain: Board

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| post_id | BIGINT | YES | | FK → bbs_post(id) ON DELETE CASCADE |
| comment_id | BIGINT | YES | | FK → bbs_comment(id) ON DELETE CASCADE |
| file_name | VARCHAR(500) | NO | | |
| stored_path | VARCHAR(500) | NO | | UNIQUE |
| mime_type | VARCHAR(150) | NO | | |
| size_bytes | BIGINT | NO | | 최대 100MB |
| checksum_sha256 | VARCHAR(64) | NO | | |
| scan_status | VARCHAR(20) | NO | 'PENDING' | PENDING/CLEAN/INFECTED/SCAN_FAILED/SKIPPED |
| scan_completed_at | TIMESTAMPTZ | YES | | |
| download_count | BIGINT | NO | 0 | |
| uploaded_by | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| uploaded_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| deleted_at | TIMESTAMPTZ | YES | | |

**Indexes:** `idx_attachment_post`, `idx_attachment_comment`, `idx_attachment_uploaded`, `idx_attachment_pending`

---

### `bbs_post_history`

> Added: V10__board_schema.sql | Domain: Board

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| post_id | BIGINT | NO | | FK → bbs_post(id) ON DELETE CASCADE |
| version | INT | NO | | UNIQUE with post_id |
| title | VARCHAR(500) | NO | | |
| content_html | TEXT | NO | | |
| edited_by | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| edit_reason | VARCHAR(200) | YES | | |
| edited_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

---

### `bbs_view_log`

> Added: V10__board_schema.sql | Domain: Board

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| post_id | BIGINT | NO | | FK → bbs_post(id) ON DELETE CASCADE |
| user_id | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| ip_hash | VARCHAR(64) | NO | | |
| user_agent_hash | VARCHAR(64) | NO | | |
| viewed_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

---

### `faq`

> Added: V10__board_schema.sql | Domain: Board

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| category_code | VARCHAR(50) | NO | | |
| question | VARCHAR(500) | NO | | |
| answer_html | TEXT | NO | | |
| answer_text | TEXT | NO | | |
| sort_order | INT | NO | 0 | |
| view_count | BIGINT | NO | 0 | |
| status | VARCHAR(20) | NO | 'PUBLISHED' | PUBLISHED/HIDDEN/DELETED |
| metadata | JSONB | YES | | |
| created_by | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| deleted_at | TIMESTAMPTZ | YES | | |

**Indexes:** `idx_faq_category`, `idx_faq_question_trgm` GIN

---

### `qna`

> Added: V10__board_schema.sql | Domain: Board

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| title | VARCHAR(500) | NO | | |
| question_html | TEXT | NO | | |
| question_text | TEXT | NO | | |
| questioner_id | BIGINT | NO | | FK → users(id) ON DELETE RESTRICT |
| answerer_id | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| answer_html | TEXT | YES | | |
| answer_text | TEXT | YES | | |
| answered_at | TIMESTAMPTZ | YES | | |
| is_private | BOOLEAN | NO | FALSE | |
| status | VARCHAR(20) | NO | 'PENDING' | PENDING/ANSWERED/CLOSED/HIDDEN |
| metadata | JSONB | YES | | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| deleted_at | TIMESTAMPTZ | YES | | |

**Indexes:** `idx_qna_status_created`, `idx_qna_questioner`, `idx_qna_answerer`, `idx_qna_title_trgm` GIN

---

### `qna_notification_optout`

> Added: V21__qna_notification_schema.sql | Domain: Board

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| user_id | BIGINT | NO | | PK, FK → users(id) ON DELETE CASCADE |
| channel | VARCHAR(20) | NO | | PK — EMAIL/KAKAO/SMS |
| opted_out_at | TIMESTAMPTZ | NO | NOW() | |

---

### `qna_notification_log`

> Added: V21__qna_notification_schema.sql | Domain: Board

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| qna_id | BIGINT | NO | | FK → qna(id) ON DELETE CASCADE |
| answerer_id | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| recipient_id | BIGINT | NO | | FK → users(id) ON DELETE CASCADE |
| channel | VARCHAR(20) | NO | | INAPP/EMAIL/KAKAO/SMS |
| status | VARCHAR(20) | NO | 'PENDING' | PENDING/SENT/FAILED/DEAD_LETTER |
| retry_count | SMALLINT | NO | 0 | |
| last_error | TEXT | YES | | |
| sent_at | TIMESTAMPTZ | YES | | |
| created_at | TIMESTAMPTZ | NO | NOW() | |

**Indexes:** `uq_qna_notif_idem` UNIQUE WHERE status IN ('SENT','PENDING'), `idx_qna_notif_pending`, `idx_qna_notif_recipient`

---

### Media 도메인

---

### `media_asset`

> Added: V12__media_schema.sql | Domain: Media

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| uuid | UUID | NO | gen_random_uuid() | UNIQUE — 공개 노출용 |
| type | VARCHAR(20) | NO | | IMAGE/VIDEO/DOCUMENT/AUDIO |
| original_filename | VARCHAR(500) | NO | | |
| stored_path | VARCHAR(500) | NO | | UNIQUE |
| public_url | VARCHAR(500) | YES | | CDN/정적 서빙 URL |
| mime_type | VARCHAR(150) | NO | | |
| size_bytes | BIGINT | NO | | 최대 5GB |
| checksum_sha256 | VARCHAR(64) | NO | | |
| width | INT | YES | | |
| height | INT | YES | | |
| duration_sec | NUMERIC(10,3) | YES | | |
| exif_stripped | BOOLEAN | NO | FALSE | |
| webp_path | VARCHAR(500) | YES | | IMAGE 타입만 |
| thumbnail_paths | JSONB | NO | '{}' | {"small","medium","large"} |
| alt_text | VARCHAR(500) | YES | | KWCAG 2.2 접근성 |
| description | TEXT | YES | | |
| tags | TEXT[] | NO | '{}' | GIN 인덱스 |
| copyright_holder | VARCHAR(200) | YES | | |
| license_type | VARCHAR(30) | NO | 'INTERNAL' | CC0/CC_BY/CC_BY_NC/PROPRIETARY/INTERNAL |
| usage_restriction | TEXT | YES | | |
| uploaded_by | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| uploaded_from_ip_hash | VARCHAR(64) | YES | | SHA-256 해시 (PII 보호) |
| status | VARCHAR(20) | NO | 'PROCESSING' | PROCESSING/READY/ARCHIVED/DELETED |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| deleted_at | TIMESTAMPTZ | YES | | |

**Indexes:** `idx_media_type_status_created`, `idx_media_uploaded_by`, `idx_media_tags_gin` GIN, `idx_media_thumb_gin` GIN, `idx_media_checksum`, `idx_media_asset_filename_trgm` GIN (V23), `idx_media_asset_description_trgm` GIN (V23)

---

### `media_asset_usage`

> Added: V12__media_schema.sql | Domain: Media

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| asset_id | BIGINT | NO | | FK → media_asset(id) ON DELETE CASCADE |
| used_in | VARCHAR(30) | NO | | POST/PAGE/CONTENT_BLOCK/COMMENT/POPUP/BANNER/EMAIL_TEMPLATE/ATTACHMENT |
| reference_id | BIGINT | NO | | 사용처 도메인 PK |
| reference_table | VARCHAR(64) | NO | | 사용처 테이블명 |
| used_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| removed_at | TIMESTAMPTZ | YES | | NULL = 활성 |

**Indexes:** `idx_usage_asset_active` WHERE removed_at IS NULL, `idx_usage_reference`

---

### `media_collection`

> Added: V12__media_schema.sql | Domain: Media

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| name | VARCHAR(200) | NO | | UNIQUE with owner_id |
| description | TEXT | YES | | |
| owner_id | BIGINT | NO | | FK → users(id) ON DELETE CASCADE |
| is_public | BOOLEAN | NO | FALSE | |
| sort_order | INT | NO | 0 | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

---

### `media_collection_item`

> Added: V12__media_schema.sql | Domain: Media

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| collection_id | BIGINT | NO | | PK, FK → media_collection(id) ON DELETE CASCADE |
| asset_id | BIGINT | NO | | PK, FK → media_asset(id) ON DELETE CASCADE |
| sort_order | INT | NO | 0 | |
| added_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

---

### `media_processing_job`

> Added: V12__media_schema.sql | Domain: Media

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| asset_id | BIGINT | NO | | FK → media_asset(id) ON DELETE CASCADE |
| job_type | VARCHAR(30) | NO | | WEBP_CONVERT/THUMBNAIL/EXIF_STRIP |
| status | VARCHAR(20) | NO | 'PENDING' | PENDING/RUNNING/SUCCESS/FAILED |
| started_at | TIMESTAMPTZ | YES | | |
| finished_at | TIMESTAMPTZ | YES | | |
| error_message | TEXT | YES | | |

**Indexes:** `idx_job_pending` WHERE status='PENDING'

---

### Content 도메인

---

### `site`

> Added: V13__content_schema.sql | Domain: Content

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| code | VARCHAR(50) | NO | | UNIQUE |
| name | VARCHAR(200) | NO | | |
| domain | VARCHAR(255) | NO | | |
| default_language | VARCHAR(10) | NO | 'ko' | ko/en |
| supported_languages | JSONB | NO | '["ko","en"]' | |
| status | VARCHAR(20) | NO | 'ACTIVE' | ACTIVE/INACTIVE |
| metadata | JSONB | YES | | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

> **시드**: code='MAIN', domain='www.example.go.kr'

---

### `menu`

> Added: V13__content_schema.sql | Domain: Content

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| site_id | BIGINT | NO | | FK → site(id) ON DELETE RESTRICT |
| parent_id | BIGINT | YES | | FK → menu(id) ON DELETE CASCADE |
| code | VARCHAR(100) | NO | | UNIQUE with site_id |
| name | VARCHAR(200) | NO | | |
| url | VARCHAR(500) | YES | | |
| target | VARCHAR(10) | NO | '_self' | _self/_blank |
| icon | VARCHAR(100) | YES | | |
| sort_order | INT | NO | 0 | |
| depth | SMALLINT | NO | 1 | 1~5 |
| path | VARCHAR(500) | NO | | materialized path |
| is_visible | BOOLEAN | NO | TRUE | |
| status | VARCHAR(20) | NO | 'ACTIVE' | ACTIVE/INACTIVE |
| metadata | JSONB | YES | | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

**Indexes:** `idx_menu_parent_sort`, `idx_menu_site_status`, `idx_menu_path`

---

### `menu_permissions`

> Added: V13__content_schema.sql | Domain: Content/Permissions

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| menu_id | BIGINT | NO | | FK → menu(id) ON DELETE CASCADE |
| role_code | VARCHAR(50) | NO | | FK → roles(code) ON DELETE CASCADE |
| permission_code | VARCHAR(100) | NO | | FK → permissions(code) ON DELETE CASCADE |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

---

### `template`

> Added: V13__content_schema.sql | Domain: Content

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| code | VARCHAR(50) | NO | | UNIQUE |
| name | VARCHAR(200) | NO | | |
| layout_type | VARCHAR(50) | NO | | FULL/SIDEBAR_LEFT/SIDEBAR_RIGHT/LANDING/BLANK |
| html_template | TEXT | NO | | Mustache 슬롯 기반 |
| css_assets | JSONB | NO | '[]' | |
| js_assets | JSONB | NO | '[]' | |
| description | TEXT | YES | | |
| status | VARCHAR(20) | NO | 'ACTIVE' | ACTIVE/INACTIVE |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

---

### `page`

> Added: V13__content_schema.sql | Domain: Content

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| site_id | BIGINT | NO | | FK → site(id) ON DELETE RESTRICT |
| template_id | BIGINT | NO | | FK → template(id) ON DELETE RESTRICT |
| menu_id | BIGINT | YES | | FK → menu(id) ON DELETE SET NULL |
| code | VARCHAR(100) | NO | | UNIQUE with site_id |
| title | VARCHAR(300) | NO | | |
| slug | VARCHAR(255) | NO | | UNIQUE with site_id |
| status | VARCHAR(20) | NO | 'DRAFT' | DRAFT/SCHEDULED/PUBLISHED/RETRACTED |
| published_at | TIMESTAMPTZ | YES | | |
| scheduled_at | TIMESTAMPTZ | YES | | |
| seo_title | VARCHAR(300) | YES | | |
| seo_description | VARCHAR(500) | YES | | |
| seo_keywords | VARCHAR(500) | YES | | |
| og_image_url | VARCHAR(500) | YES | | |
| canonical_url | VARCHAR(500) | YES | | |
| current_version | INT | NO | 1 | |
| created_by | BIGINT | NO | | FK → users(id) |
| updated_by | BIGINT | YES | | FK → users(id) |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| deleted_at | TIMESTAMPTZ | YES | | |

**Indexes:** `idx_page_slug_status`, `idx_page_site_status`, `idx_page_scheduled`, `idx_page_title_trgm` GIN (V23)

---

### `content_block`

> Added: V13__content_schema.sql | Domain: Content

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| page_id | BIGINT | NO | | FK → page(id) ON DELETE CASCADE |
| block_type | VARCHAR(20) | NO | | RICH_TEXT/IMAGE/HTML/MARKDOWN/EMBED |
| sort_order | INT | NO | 0 | |
| payload | JSONB | NO | | 블록 타입별 스키마 |
| version | INT | NO | 1 | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

---

### `page_history`

> Added: V13__content_schema.sql | Domain: Content

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| page_id | BIGINT | NO | | FK → page(id) ON DELETE CASCADE |
| version | INT | NO | | UNIQUE with page_id |
| snapshot | JSONB | NO | | page + content_block + i18n_resource 전체 스냅샷 |
| edited_by | BIGINT | NO | | FK → users(id) |
| edited_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| change_summary | VARCHAR(500) | YES | | |

---

### `popup`

> Added: V13__content_schema.sql | Domain: Content

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| site_id | BIGINT | NO | | FK → site(id) ON DELETE RESTRICT |
| title | VARCHAR(300) | NO | | |
| content_html | TEXT | NO | | |
| position | VARCHAR(20) | NO | 'CENTER' | CENTER/TOP_RIGHT/BOTTOM_RIGHT/TOP_LEFT/BOTTOM_LEFT/CUSTOM |
| x_offset | INT | YES | | CUSTOM일 때 사용 |
| y_offset | INT | YES | | |
| width | INT | NO | 400 | |
| height | INT | NO | 300 | |
| show_from | TIMESTAMPTZ | NO | | |
| show_until | TIMESTAMPTZ | NO | | |
| show_today_close | BOOLEAN | NO | TRUE | |
| display_priority | INT | NO | 0 | |
| target_type | VARCHAR(20) | NO | 'ALL' | ALL/MEMBER/ROLE |
| target_role_codes | JSONB | NO | '[]' | |
| status | VARCHAR(20) | NO | 'ACTIVE' | ACTIVE/INACTIVE |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

**Indexes:** `idx_popup_active_window` WHERE status='ACTIVE'

---

### `banner`

> Added: V13__content_schema.sql | Domain: Content

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| site_id | BIGINT | NO | | FK → site(id) ON DELETE RESTRICT |
| banner_group_code | VARCHAR(50) | NO | | |
| title | VARCHAR(300) | NO | | |
| image_url | VARCHAR(500) | NO | | |
| link_url | VARCHAR(500) | YES | | |
| link_target | VARCHAR(10) | NO | '_self' | |
| alt_text | VARCHAR(300) | NO | | KWCAG 2.2 AA — NOT NULL 강제 |
| display_from | TIMESTAMPTZ | NO | | |
| display_until | TIMESTAMPTZ | NO | | |
| sort_order | INT | NO | 0 | |
| click_count | BIGINT | NO | 0 | |
| status | VARCHAR(20) | NO | 'ACTIVE' | ACTIVE/INACTIVE |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

**Indexes:** `idx_banner_group_active` WHERE status='ACTIVE'

---

### `i18n_resource`

> Added: V13__content_schema.sql | Domain: Content

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| namespace | VARCHAR(50) | NO | | menu/page/popup/banner/content_block/system |
| resource_id | BIGINT | NO | | UNIQUE with namespace, language, field_name |
| language | VARCHAR(10) | NO | | ko/en |
| field_name | VARCHAR(100) | NO | | |
| value | TEXT | NO | | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

**Indexes:** `idx_i18n_lookup` ON (namespace, resource_id, language)

---

### `seo_redirect`

> Added: V13__content_schema.sql | Domain: Content

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| from_path | VARCHAR(500) | NO | | UNIQUE |
| to_path | VARCHAR(500) | NO | | |
| http_status | SMALLINT | NO | 301 | 301/302 |
| is_active | BOOLEAN | NO | TRUE | |
| reason | VARCHAR(200) | YES | | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

---

### System 도메인

---

### `access_log`

> Added: V14__system_schema.sql | Domain: System | PARTITION BY RANGE(created_at) 월별

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK (복합: id, created_at) |
| site_id | BIGINT | NO | 1 | |
| user_id | BIGINT | YES | | |
| session_id | VARCHAR(128) | YES | | |
| ip_hash | CHAR(64) | NO | | SHA-256 익명화 |
| user_agent | TEXT | YES | | |
| referrer | TEXT | YES | | |
| page_url | TEXT | NO | | |
| status_code | SMALLINT | NO | | |
| response_time_ms | INT | NO | 0 | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | 파티션 키 |

> 파티션: access_log_y2026m04, access_log_y2026m05. 보존정책: 3개월 DELETE.

---

### `access_stat_daily` / `access_stat_monthly`

> Added: V14__system_schema.sql | Domain: System

일별/월별 접속 통계 집계 테이블. PK: (stat_date, site_id) / (stat_month, site_id)

---

### `code_group` / `code`

> Added: V14__system_schema.sql | Domain: System

공통코드 그룹/코드 CRUD. code_group.group_code → code.group_code FK.

> **시드**: BOARD_TYPE, USER_STATUS, MAINTENANCE_REASON 그룹 + 각 코드 값

---

### `system_setting`

> Added: V14__system_schema.sql | Domain: System

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| key | VARCHAR(100) | NO | | PK |
| value | TEXT | NO | | |
| value_type | VARCHAR(10) | NO | 'STRING' | STRING/INT/BOOL/JSON |
| description | TEXT | YES | | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

---

### `maintenance`

> Added: V14__system_schema.sql | Domain: System

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| title | VARCHAR(200) | NO | | |
| message_ko | TEXT | YES | | |
| message_en | TEXT | YES | | |
| start_at | TIMESTAMPTZ | NO | | |
| end_at | TIMESTAMPTZ | NO | | |
| status | VARCHAR(20) | NO | 'SCHEDULED' | SCHEDULED/ACTIVE/COMPLETED/CANCELLED |
| allow_admin_access | BOOLEAN | NO | TRUE | |
| created_by | BIGINT | YES | | |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

---

### Safety 도메인

---

### `safety_incident`

> Added: V15__safety_schema.sql | Domain: Safety

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| source_type | VARCHAR(50) | NO | | |
| industry_code | VARCHAR(20) | NO | | |
| occupation_code | VARCHAR(20) | YES | | |
| process_type | VARCHAR(50) | YES | | |
| incident_type | VARCHAR(50) | NO | | |
| occurred_at | TIMESTAMPTZ | NO | | |
| severity | VARCHAR(20) | NO | | |
| casualties | INT | NO | 0 | |
| location | VARCHAR(200) | YES | | |
| summary | TEXT | NO | | |
| detailed_cause | TEXT | YES | | |
| prevention_lesson | TEXT | YES | | |
| source_url | VARCHAR(500) | YES | | |
| search_vector | TSVECTOR | YES | | GIN 인덱스 |
| status | VARCHAR(20) | NO | 'PUBLISHED' | |
| created_at | TIMESTAMPTZ | NO | now() | |
| updated_at | TIMESTAMPTZ | NO | now() | |

**Indexes:** `idx_safety_incident_industry`, `idx_safety_incident_type`, `idx_safety_incident_occurred`, `idx_safety_incident_search` GIN

---

### Safety 관련 테이블 (V15)

- **`safety_keyword`**: 키워드 사전 (category + code UNIQUE)
- **`safety_keyword_synonym`**: 동의어 (keyword_id FK)
- **`safety_incident_keyword`**: 사고-키워드 N:M 매핑 (weight 포함)
- **`company_safety_profile`**: 기업 안전 프로필 (company_id=users.id UNIQUE)
- **`safety_match_result`**: 매칭 결과 TTL 캐시 (expires_at = now()+1h)
- **`safety_guideline_template`**: 가이드라인 템플릿 (code UNIQUE, applicable_industry_codes TEXT[])
- **`safety_guideline_report`**: 생성된 보고서 (uuid UNIQUE)
- **`safety_checklist_item`**: 체크리스트 항목 (template_id FK)
- **`safety_check_result`**: 체크 결과 (report_id + item_id UNIQUE)

---

### Policy 도메인

---

### `departments` (stub)

> Added: V16__policy_schema.sql | Domain: Policy — SPEC-CMS-002 조직 부서 stub

### `notification_template` (stub)

> Added: V16__policy_schema.sql | Domain: Policy/Notification — SPEC-CMS-004 알림 템플릿 stub

---

### `policy_data_source`

> Added: V16__policy_schema.sql | Domain: Policy

외부 OpenAPI 소스 관리. auth_secret_ref는 Secrets Manager 참조 형식 (평문 금지).

---

### `policy_program`

> Added: V16__policy_schema.sql | Modified: V33 (embed_vector, embedded_at, embed_model_version) | Domain: Policy

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| code | VARCHAR(100) | NO | | UNIQUE |
| ministry | VARCHAR(50) | NO | | |
| program_name | VARCHAR(300) | NO | | |
| program_name_i18n | JSONB | NO | '{}' | |
| description_html | TEXT | YES | | |
| target_industries | TEXT[] | NO | '{}' | GIN 인덱스 |
| target_regions | TEXT[] | NO | '{}' | GIN 인덱스 |
| min/max_employees | INT | YES | | |
| min/max_revenue | BIGINT | YES | | |
| min/max_business_age_months | INT | YES | | |
| application_start/end | TIMESTAMPTZ | YES | | |
| budget_total/per_company | BIGINT | YES | | |
| source_url | VARCHAR(500) | YES | | |
| source_id | BIGINT | YES | | FK → policy_data_source(id) |
| status | VARCHAR(20) | NO | 'DRAFT' | DRAFT/ACTIVE/CLOSED/EXPIRED |
| created_at | TIMESTAMPTZ | NO | now() | |
| updated_at | TIMESTAMPTZ | NO | now() | |
| embed_vector | vector(384) | YES | | sentence embedding 384차원 (V33, pgvector) |
| embedded_at | TIMESTAMPTZ | YES | | 임베딩 생성 시점 (NULL=미생성) |
| embed_model_version | VARCHAR(64) | YES | | 임베딩 생성 모델 버전 식별자 |

**Indexes:** `idx_pp_status_app`, `idx_pp_industries` GIN, `idx_pp_regions` GIN, `idx_policy_program_name_trgm` GIN (V23), `idx_policy_program_desc_html_trgm` GIN (V23), `idx_policy_program_embed_cosine` IVFFlat (embed_vector vector_cosine_ops, lists=100) (V33)

---

### Policy 관련 테이블 (V16)

- **`policy_eligibility_rule`**: 자격요건 규칙 (rule_type: INCLUDE/EXCLUDE, dimension, operator)
- **`policy_keyword`**: 정책 키워드 (weight 포함)
- **`company_match_input`**: 기업 매칭 입력 프로필 (company_id UNIQUE)
- **`policy_match_score`**: 기업-정책 매칭 점수 TTL 캐시 (grade: A/B/C/D)
- **`notification_subscription`**: 알림 수신 동의 (channel + category per user UNIQUE)
- **`notification_dispatch_schedule`**: 발송 예약 (schedule_uuid UNIQUE)
- **`notification_dispatch_target`**: 발송 대상 (idempotency_key UNIQUE)
- **`policy_application_log`**: 정책 신청/클릭 추적 (source, action)

---

### Dashboard 도메인

---

### KPI 테이블 (V17)

- **`kpi_definition`**: KPI 메타정보 (code UNIQUE, calculation_query, refresh_interval_min)
- **`kpi_value`**: KPI 현재값 (kpi_id + dimension UNIQUE)
- **`kpi_value_history`**: KPI 이력 (archived_at 포함)

---

### Dashboard 테이블 (V17)

- **`dashboard_widget`**: 위젯 정의 (widget_type 9종, data_source 3종)
- **`dashboard_layout`**: 사용자별 레이아웃 (owner_id + name UNIQUE, is_default 1개만)
- **`dashboard_layout_widget`**: 레이아웃-위젯 매핑 (layout_id + instance_id PK)
- **`saved_view`**: 저장된 필터/뷰 (filter_state JSONB)
- **`chart_dataset_cache`**: 차트 데이터 캐시 (TTL 5분, cache_key UNIQUE)
- **`export_history`**: 내보내기 이력 (TTL 24시간, EXCEL/CSV/PDF)

---

### Governance 도메인

---

### `data_dictionary`

> Added: V18__governance_schema.sql | Domain: Governance

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| table_name | VARCHAR(80) | NO | | UNIQUE with column_name |
| column_name | VARCHAR(80) | NO | | |
| logical_name_ko | VARCHAR(200) | NO | | 한글 논리명 |
| logical_name_en | VARCHAR(200) | YES | | |
| data_domain | VARCHAR(20) | NO | | MASTER/TRANSACTION/STATISTICS/LOG |
| data_type | VARCHAR(50) | NO | | |
| description | TEXT | YES | | |
| is_pii | BOOLEAN | NO | FALSE | 개인정보 여부 |
| is_required | BOOLEAN | NO | FALSE | |
| status | VARCHAR(20) | NO | 'ACTIVE' | ACTIVE/DEPRECATED/REMOVED |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

> V24에서 PII email 컬럼 5개 시드 등록. V26에서 users.email status='REMOVED'로 갱신.

---

### Governance 관련 테이블 (V18)

- **`data_dictionary_history`**: 표준 사전 변경 이력 (dictionary_id FK)
- **`retention_policy`**: 보존/이관 정책 (target_table UNIQUE — 7건 시드)
- **`batch_execution_log`**: 배치 실행 이력 (job_group: STATS/RETENTION/QUALITY/RECOVERY)
- **`data_quality_rule`**: 품질 룰 정의 (rule_type 5종 — 8건 시드)
- **`data_quality_report`**: 품질 검사 결과 (violation BOOLEAN)
- **`recovery_drill_log`**: 복구 시험 이력 (RTO 240분/RPO 60분 목표)

### Statistics 테이블 (V18)

- **`board_stats_daily`** / **`board_stats_monthly`**: 게시판 통계
- **`content_view_stats_daily`** / **`content_view_stats_monthly`**: 콘텐츠 조회 통계
- **`policy_match_stats_monthly`**: 정책 매칭 성공률
- **`safety_stats_monthly`**: 안전사고 추이

---

### Publication 도메인

---

### `publication_category`

> Added: V19__publication_schema.sql | Domain: Publication

계층형 카테고리 (depth 1~3, 트리거로 자동 계산). code UNIQUE.

### `bbs_post_publication_meta`

> Added: V19__publication_schema.sql | Domain: Publication

bbs_post 1:1 확장 (post_id PK). publication_year, document_type(REPORT/BROCHURE/RESEARCH/GUIDE/OTHER), isbn, publisher.

### `publication_download_stat`

> Added: V19__publication_schema.sql | Domain: Publication

일별 다운로드 집계 (post_id + attachment_id + stat_date PK).

### `publication_zip_archive`

> Added: V19__publication_schema.sql | Domain: Publication | TTL 7일

ZIP 다운로드 아카이브 (download_id UUID UNIQUE, mode: SYNC/ASYNC, expires_at = now()+7days).

---

### Survey 도메인

---

### `survey`

> Added: V20__survey_schema.sql | Domain: Survey

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGINT IDENTITY | NO | | PK |
| bbs_id | BIGINT | YES | | FK → bbs_master(id) ON DELETE SET NULL |
| title | VARCHAR(500) | NO | | |
| start_at | TIMESTAMPTZ | NO | | |
| end_at | TIMESTAMPTZ | NO | | |
| target_role_codes | JSONB | YES | | |
| is_anonymous | BOOLEAN | NO | FALSE | |
| max_responses | INT | YES | | |
| response_count | INT | NO | 0 | |
| status | VARCHAR(20) | NO | 'DRAFT' | DRAFT/OPEN/CLOSED/HIDDEN |
| created_by | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| deleted_at | TIMESTAMPTZ | YES | | |

### `survey_question`

> Added: V20__survey_schema.sql | Domain: Survey

질문 타입: SINGLE/MULTI/TEXT/RATING/DATE. SINGLE/MULTI는 options JSONB 필수.

### `survey_response`

> Added: V20__survey_schema.sql | Domain: Survey

응답 헤더. 동일 사용자 중복 응답 방지 UNIQUE 인덱스.

### `survey_answer`

> Added: V20__survey_schema.sql | Domain: Survey

질문별 답변. answer_text / answer_options JSONB / answer_rating(1~5) / answer_date.

---

### Search 도메인

---

### `search_log`

> Added: V22__search_schema.sql | Domain: Search | INSERT-ONLY (시계열)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| user_id | BIGINT | YES | | FK → users(id) ON DELETE SET NULL |
| session_id | VARCHAR(64) | NO | | |
| query | VARCHAR(200) | NO | | 원본 쿼리 |
| normalized_query | VARCHAR(200) | NO | | 공백제거+소문자 (집계 키) |
| expanded_query | VARCHAR(500) | YES | | 동의어 확장 후 |
| result_count | INTEGER | NO | 0 | |
| response_ms | INTEGER | NO | 0 | |
| clicked_doc_type | VARCHAR(30) | YES | | board/content/policy/safety/media/publication |
| clicked_doc_id | BIGINT | YES | | |
| clicked_at | TIMESTAMPTZ | YES | | |
| clicked_rank | INTEGER | YES | | |
| locale | VARCHAR(10) | NO | 'ko' | ko/en |
| domain_filter | VARCHAR(20) | NO | 'ALL' | |
| ip_hash | VARCHAR(64) | YES | | SHA-256 |
| created_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |

**Indexes:** `idx_sl_created_brin` BRIN (시계열 최적화), `idx_sl_normalized_time`, `idx_sl_user_time`, `idx_sl_zero_result`

### `search_popular_cache`

> Added: V22__search_schema.sql | Domain: Search

인기 검색어 캐시 (period_type: DAILY/WEEKLY/MONTHLY, rank 포함). 보존정책: 24개월 DELETE.

### `search_synonym`

> Added: V22__search_schema.sql | Domain: Search

동의어 사전 (term + synonym + locale UNIQUE, term ≠ synonym). soft delete (status=PAUSED).

---

### PII / Security 도메인

---

### `pii_key_rotation_log`

> Added: V25__pii_key_rotation_log.sql | Domain: PII/Security

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| started_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| finished_at | TIMESTAMPTZ | YES | | NULL = 진행 중 |
| old_key_version | SMALLINT | NO | | |
| new_key_version | SMALLINT | NO | | |
| migrated_rows | INTEGER | NO | 0 | 재암호화 완료 row 수 |
| status | VARCHAR(20) | NO | 'IN_PROGRESS' | IN_PROGRESS/COMPLETED/FAILED |
| error_message | TEXT | YES | | |

---

### AI 도메인

---

### `ai_prediction_log`

> Added: V28__ai_prediction_log.sql | Domain: AI | SPEC: SPEC-CMS-AI-001

ML 추론 호출 전체 적재 테이블. 모니터링·드리프트 분석 기반.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| prediction_type | VARCHAR(20) | NO | | GROWTH_STAGE / RISK_SCORE / SIMULATION |
| model_name | VARCHAR(100) | NO | | |
| model_version | VARCHAR(20) | NO | | |
| request_ref | VARCHAR(100) | YES | | 요청 추적 키 (평문 미저장 권장) |
| input_features | JSONB | NO | | PII 제외 입력 피처 |
| output_result | JSONB | YES | | 모델 출력 |
| confidence | NUMERIC(5,4) | YES | | |
| latency_ms | INTEGER | YES | | |
| status | VARCHAR(20) | NO | | SUCCESS / ML_ERROR / TIMEOUT / FALLBACK |
| actual_value | JSONB | YES | | 실제값 (사후 레이블링) |
| predicted_at | TIMESTAMPTZ | NO | now() | |
| labeled_at | TIMESTAMPTZ | YES | | 레이블 적재 시각 |

**Indexes:** `idx_ai_prediction_log_type`, `idx_ai_prediction_log_status`, `idx_ai_prediction_log_predicted_at` DESC

---

### `ai_simulation_session`

> Added: V29__ai_simulation_session.sql | Domain: AI | SPEC: SPEC-CMS-AI-001

익명 시뮬레이션 세션. **평문 IP 절대 미저장** — `client_ip_hash` SHA-256(64자)만 저장. 24시간 TTL.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | UUID | NO | gen_random_uuid() | PK |
| ksic_code | VARCHAR(5) | NO | | 한국표준산업분류 코드 |
| capital_amount | BIGINT | NO | | |
| founding_year | INTEGER | NO | | |
| revenue_amount | BIGINT | YES | | |
| projection_result | JSONB | YES | | 시뮬레이션 결과 |
| pdf_status | VARCHAR(20) | NO | 'NONE' | NONE / GENERATING / READY / FAILED |
| client_ip_hash | VARCHAR(64) | NO | | SHA-256(IP) — IpHashUtil 재사용 |
| created_at | TIMESTAMPTZ | NO | now() | |
| expires_at | TIMESTAMPTZ | NO | now()+24h | TTL 만료 시각 |

**Indexes:** `idx_ai_simulation_session_ip_hash` ON (client_ip_hash, created_at DESC), `idx_ai_simulation_session_expires` ON (expires_at)

---

### `ai_model_metric`

> Added: V30__ai_model_metric.sql | Domain: AI | SPEC: SPEC-CMS-AI-001

모델/예측유형/집계주기/기간 UNIQUE upsert. 드리프트 감지 플래그 포함.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| model_name | VARCHAR(100) | NO | | |
| prediction_type | VARCHAR(20) | NO | | GROWTH_STAGE / RISK_SCORE / SIMULATION |
| aggregate_period | VARCHAR(10) | NO | | DAILY / WEEKLY / MONTHLY |
| period_start | DATE | NO | | |
| rmse | NUMERIC(10,4) | YES | | |
| mae | NUMERIC(10,4) | YES | | |
| accuracy | NUMERIC(5,4) | YES | | |
| latency_p50 | INTEGER | YES | | ms |
| latency_p95 | INTEGER | YES | | ms |
| latency_p99 | INTEGER | YES | | ms |
| sample_count | INTEGER | NO | 0 | |
| drift_detected | BOOLEAN | NO | false | |
| created_at | TIMESTAMPTZ | NO | now() | |

**Unique:** `uq_ai_model_metric` (model_name, prediction_type, aggregate_period, period_start)

**Indexes:** `idx_ai_model_metric_drift` ON (drift_detected, created_at DESC)

---

### `ai_retrain_queue`

> Added: V31__ai_retrain_queue.sql | Domain: AI | SPEC: SPEC-CMS-AI-001

드리프트 자동 또는 수동 요청으로 모델 재학습 작업을 큐잉.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| model_name | VARCHAR(100) | NO | | |
| trigger_reason | VARCHAR(30) | NO | | DRIFT_ACCURACY / DRIFT_ERROR / MANUAL |
| trigger_detail | JSONB | YES | | 트리거 상세 정보 |
| status | VARCHAR(20) | NO | 'QUEUED' | QUEUED → ACKNOWLEDGED → IN_PROGRESS → DONE / CANCELED |
| requested_by | BIGINT | YES | | FK → users(id) (nullable — 자동 트리거 시 NULL) |
| requested_at | TIMESTAMPTZ | NO | now() | |
| updated_at | TIMESTAMPTZ | NO | now() | |

**Indexes:** `idx_ai_retrain_queue_status` ON (status, requested_at DESC)

---

### `ai_policy_recommendation_log`

> Added: V32__create_ai_policy_recommendation_log.sql | Domain: AI | SPEC: SPEC-CMS-AI-002

AI 정책 추천 및 피드백 로그. **PII 완전 배제** — `session_ref` SHA-256, `company_profile` PII 화이트리스트만 허용.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| session_ref | VARCHAR(80) | NO | | SHA-256 해시 (평문 식별자 미저장) |
| company_profile | JSONB | NO | | PII 화이트리스트: ksic_code/employee_count/growth_stage/region_code/annual_revenue |
| query_text | VARCHAR(500) | YES | | 선택적 자연어 검색어 |
| recommended_policy_ids | JSONB | YES | | 순서 보존 추천 정책 ID 배열 |
| ml_scores | JSONB | YES | | {"policy_id": {"semantic":N,"rule":N,"hybrid":N}} |
| interaction_type | VARCHAR(20) | NO | | VIEWED / CLICKED / APPLIED / DISMISSED |
| policy_id | BIGINT | YES | | 상호작용 정책 ID (VIEWED=NULL, 나머지=필수) |
| recommended_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| interacted_at | TIMESTAMPTZ | YES | | 피드백 행만 |

**Indexes:** `idx_aprl_session` ON (session_ref, recommended_at DESC), `idx_aprl_type_time`, `idx_aprl_policy_time` WHERE policy_id IS NOT NULL, `idx_aprl_metrics_day`

---

### `ai_rag_query_log`

> Added: V33__ai_rag_query_log_and_policy_embedding.sql | Domain: AI | SPEC: SPEC-CMS-AI-003

RAG 질의/피드백 로그. **질문 평문 미저장** — `question_hash` SHA-256. `query_ref` 피드백 상관키(멱등 갱신).

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | BIGSERIAL | NO | nextval | PK |
| query_ref | VARCHAR(64) | NO | | UNIQUE, 클라이언트 반환 UUID — 피드백 상관키 |
| question_hash | VARCHAR(80) | NO | | 질문 SHA-256 (ragQueryCache 키와 동일 산식) |
| session_ref | VARCHAR(80) | NO | | SHA-256 해시 (V32와 동일 규칙) |
| retrieved_policy_ids | JSONB | YES | | 검색된 정책 ID 배열 |
| answer_quality_score | SMALLINT | YES | | 0~100 (ML/규칙 산출) |
| feedback | VARCHAR(20) | YES | | HELPFUL / UNHELPFUL (NULL=미응답) |
| latency_ms | INTEGER | NO | | 전체 처리 지연(ms) |
| cache_hit | BOOLEAN | NO | false | ragQueryCache 히트 여부 |
| degraded | BOOLEAN | NO | false | FTS 단독 폴백 여부 |
| queried_at | TIMESTAMPTZ | NO | CURRENT_TIMESTAMP | |
| feedback_at | TIMESTAMPTZ | YES | | 피드백 발생 시각 (feedback과 동시 NULL 또는 동시 NOT NULL) |

**Indexes:** `idx_arql_qhash` ON (question_hash, queried_at DESC), `idx_arql_session`, `idx_arql_feedback` WHERE feedback IS NOT NULL, `idx_arql_metrics_day`, `idx_arql_degraded`

---

## Relationships (주요)

| 관계 | 설명 |
|------|------|
| users 1:N user_roles | 사용자 다중 역할 |
| roles 1:N user_roles | 역할-사용자 N:M |
| roles 1:N role_permissions | 역할-권한 N:M |
| users N:1 organization | 사용자 소속 조직 |
| organization 1:N organization | 자기 참조 계층 트리 (depth ≤ 5) |
| users 1:N refresh_tokens | 활성 토큰 복수 보유 |
| site 1:N menu | 사이트-메뉴 트리 |
| site 1:N page | 사이트-페이지 |
| page 1:N content_block | 페이지-블록 |
| template 1:N page | 템플릿 재사용 |
| bbs_master 1:N bbs_post | 게시판-게시글 |
| bbs_post 1:N bbs_comment | 게시글-댓글 (1단계 대댓글) |
| bbs_post 1:1 bbs_post_publication_meta | 발간자료 메타 확장 |
| qna 1:N qna_notification_log | Q&A 알림 추적 |
| media_asset 1:N media_asset_usage | 사용처 Reference Counting |
| media_asset N:M media_collection (via item) | 컬렉션-자산 |
| policy_program 1:N policy_eligibility_rule | 정책-자격요건 |
| company_safety_profile 1:N safety_match_result | 기업-사고 매칭 |
| safety_guideline_template 1:N safety_checklist_item | 템플릿-체크리스트 |
| survey 1:N survey_question → survey_response 1:N survey_answer | 설문 계층 |
| kpi_definition 1:N kpi_value | KPI 현재값 |
| dashboard_layout 1:N dashboard_layout_widget | 레이아웃-위젯 |
| data_quality_rule 1:N data_quality_report | 품질 룰-검사 결과 |
| ai_prediction_log (독립) | ML 추론 감사 로그 (외래키 없음) |
| ai_simulation_session (독립) | 익명 시뮬레이션 세션 (외래키 없음, TTL 24h) |
| ai_model_metric (독립) | 모델 성능 집계 (UNIQUE upsert 패턴) |
| ai_retrain_queue N:1 users | 수동 재학습 요청자 (NULL=자동 트리거) |
| ai_policy_recommendation_log N:1 policy_program (via policy_id) | 정책 추천-피드백 연결 |
| ai_rag_query_log (독립) | RAG 질의/피드백 로그 (평문 미저장) |
| policy_program embed_vector | pgvector IVFFlat cosine 검색 지원 (V33) |

---

## Notes

### PII / 보안

- **V26 (BREAKING)**: `users.email` 평문 컬럼 DROP. 롤백 불가. `email_encrypted` + `email_hmac` 경로 전용.
- **V24**: email AES-256-GCM 암호화 (`email_encrypted`, `email_iv`, `email_tag`, `email_hmac`, `email_key_version`) 추가.
- **V25**: PII 키 회전 배치 이력 (`pii_key_rotation_log`) 추가.
- IP 주소는 직접 저장 금지 — SHA-256 해시(ip_hash)만 저장.

### APPEND-ONLY 테이블 (UPDATE/DELETE 트리거 차단)

- `audit_log` (V3) — 5년 보존
- `permission_change_history` (V7)
- `personal_data_access_log` (V9) — 6개월 ARCHIVE

### TTL 캐시 테이블

| 테이블 | TTL |
|--------|-----|
| safety_match_result | 1시간 |
| policy_match_score | expires_at 기준 |
| chart_dataset_cache | 5분 |
| export_history | 24시간 |
| publication_zip_archive | 7일 |

### 파티션 테이블

- `access_log`: PARTITION BY RANGE(created_at) 월별 — 보존 3개월 DELETE

### 자동 갱신 트리거

- `bbs_post.search_vector`: title + content_text → tsvector 자동 갱신
- `bbs_comment`: 1단계 대댓글 깊이 제한
- `publication_category.depth`: parent_id 기반 자동 계산

### V23 추가 검색 성능 인덱스 (ILIKE fallback 가속)

- page.title, policy_program.program_name/description_html, media_asset.original_filename/description, users.username/name — 모두 GIN gin_trgm_ops

### AI 도메인 — PII / 익명화 원칙 (V28~V33)

- **IP 주소**: `ai_simulation_session.client_ip_hash` SHA-256(64자) — IpHashUtil 재사용. 평문 IP 절대 미저장.
- **세션/회원 식별자**: `ai_policy_recommendation_log.session_ref`, `ai_rag_query_log.session_ref` — SHA-256 해시 전용. 평문 미저장.
- **질문 텍스트**: `ai_rag_query_log.question_hash` — SHA-256 해시 전용. 평문 미저장.
- **회사 프로필**: `ai_policy_recommendation_log.company_profile` — PII 화이트리스트 5개 필드만 허용(ksic_code/employee_count/growth_stage/region_code/annual_revenue). 대표자명·법인식별번호 금지.

### V27 Board 소프트 삭제

- `bbs_master.deleted_at` 추가 — BbsMasterMapper.xml에서 deleted_at IS NULL 조건으로 활성 게시판 조회.
- `idx_bbs_master_active` 부분 인덱스로 ACTIVE 게시판 조회 성능 보장.

### V33 pgvector 확장 및 임베딩

- **운영 이미지**: `pgvector/pgvector:pg16` 필수 (`postgres:16-alpine` → 교체됨).
- **embed_vector**: `policy_program.embed_vector vector(384)` — sentence embedding 384차원. NULL=미생성(임베딩 배치 미실행).
- **IVFFlat 인덱스**: `lists=100` — 운영 데이터 적재 후 리스트 수 튜닝 필요(일반적으로 `rows/1000` ~ `sqrt(rows)`).
- **AI 질의 피드백 TTL**: `ai_rag_query_log.feedback`은 비동기 갱신 — `query_ref`로 멱등 UPDATE 처리.
