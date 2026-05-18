# Migrations

마이그레이션 버전 이력 및 롤백 메모 (V1–V33, Flyway 순번 기준).
자동 생성일: 2026-05-15 / 최종 갱신일: 2026-05-18 / 최종 적용 버전: V33

---

## Applied Migrations

| Version | Filename | Summary | Breaking |
|---------|----------|---------|----------|
| V1  | `V1__init_baseline.sql` | pgcrypto · pg_trgm 확장 설치, DB 기반선 설정 | No |
| V2  | `V2__auth_schema.sql` | 인증 도메인: users, roles, user_roles, password_history, login_history, refresh_tokens, token_blacklist 생성 + 기본 역할 시드 | No |
| V3  | `V3__audit_log.sql` | APPEND-ONLY audit_log 생성 (INSERT-only trigger) | No |
| V4  | `V4__seed_admin_user.sql` | 개발/테스트용 관리자 계정 시드 (BCrypt) | No |
| V5  | `V5__organization_schema.sql` | 조직 도메인: organization(자기참조 트리, depth≤5, materialized path), organization_history 생성 + users.organization_id FK 추가 + ROOT 조직 시드 | No |
| V6  | `V6__permissions_schema.sql` | RBAC: permissions, role_permissions 생성 + 기본 권한 시드 (RESOURCE:ACTION 패턴) | No |
| V7  | `V7__permission_change_history.sql` | APPEND-ONLY permission_change_history 생성 (INSERT-only trigger) | No |
| V8  | `V8__verification_schema.sql` | 인증 요청 도메인: verification_request, verification_history 생성 (OTP/이메일 인증) | No |
| V9  | `V9__personal_data_access_log.sql` | 개인정보 접근 이력: personal_data_access_log (APPEND-ONLY) + personal_data_access_log_archive 생성 | No |
| V10 | `V10__board_schema.sql` | 게시판 도메인: bbs_master, bbs_post (search_vector tsvector + 자동갱신 trigger), bbs_comment (1-depth trigger), bbs_attachment, faq, qna, bbs_post_history, bbs_view_log 생성 | No |
| V11 | _(없음)_ | 버전 번호 결번 (의도적 건너뜀) | — |
| V12 | `V12__media_schema.sql` | 미디어 도메인: media_asset, media_asset_usage, media_collection, media_collection_item, media_processing_job 생성 | No |
| V13 | `V13__content_schema.sql` | 콘텐츠 도메인: site, menu, menu_permissions, template, page, content_block, page_history, popup, banner, i18n_resource, seo_redirect 생성 + MAIN 사이트 시드 | No |
| V14 | `V14__system_schema.sql` | 시스템 도메인: access_log (PARTITION BY RANGE 월별), access_stat_daily/monthly, code_group, code, system_setting, maintenance 생성 + 코드 시드 + SYSTEM 권한 시드 | No |
| V15 | `V15__safety_schema.sql` | 안전보건 도메인: safety_incident (search_vector), safety_keyword, safety_keyword_synonym, safety_incident_keyword, company_safety_profile, safety_match_result (TTL 1h), safety_guideline_template, safety_guideline_report, safety_checklist_item, safety_check_result 생성 | No |
| V16 | `V16__policy_schema.sql` | 정책 도메인: policy_program, policy_eligibility_rule, policy_keyword, company_match_input, policy_match_score (TTL), notification_subscription, notification_dispatch_schedule/target, policy_application_log, notification_template (stub), departments (stub) 생성 | No |
| V17 | `V17__dashboard_schema.sql` | 대시보드 도메인: kpi_definition, kpi_value, kpi_value_history, dashboard_widget, dashboard_layout, dashboard_layout_widget, saved_view, chart_dataset_cache (TTL 5min), export_history (TTL 24h) 생성 | No |
| V18 | `V18__governance_schema.sql` | 거버넌스/데이터품질: data_dictionary, data_dictionary_history, retention_policy, batch_execution_log, 통계 테이블 6종, data_quality_rule, data_quality_report, recovery_drill_log 생성 + 보존 정책/품질 규칙 시드 | No |
| V19 | `V19__publication_schema.sql` | 간행물 도메인: publication_category, bbs_post_publication_meta, publication_download_stat, publication_zip_archive (TTL 7d) 생성 | No |
| V20 | `V20__survey_schema.sql` | 설문 도메인: survey, survey_question, survey_response, survey_answer 생성 | No |
| V21 | `V21__qna_notification_schema.sql` | Q&A 알림: qna_notification_optout, qna_notification_log 생성 | No |
| V22 | `V22__search_schema.sql` | 검색 도메인: search_log (BRIN 인덱스, 시계열 최적화), search_popular_cache, search_synonym 생성 + 보존 정책 시드 2건 | No |
| V23 | `V23__search_performance_indexes.sql` | 성능 인덱스 전용 (신규 테이블 없음): page.title, policy_program.program_name/description_html, media_asset.original_filename/description, users.username/name — GIN gin_trgm_ops 인덱스 추가 (ILIKE 가속) | No |
| V24 | `V24__pii_encryption_email.sql` | **PII 보안**: users 테이블에 email_encrypted(BYTEA), email_iv, email_tag, email_hmac(VARCHAR 64, UNIQUE 부분인덱스), email_key_version 컬럼 추가. email 컬럼 NOT NULL 해제 (V26에서 DROP 예정). data_dictionary PII 시드 5건 | **PII** |
| V25 | `V25__pii_key_rotation_log.sql` | **PII 보안**: pii_key_rotation_log 테이블 생성 (IN_PROGRESS/COMPLETED/FAILED 상태, 키 버전 추적, 청크 커밋 결합) | **PII** |
| V26 | `V26__drop_email_plain_column.sql` | **BREAKING (비가역)**: `users.email` 평문 컬럼 DROP. data_dictionary email 상태 REMOVED 갱신. email_encrypted/email_hmac 경로가 표준 경로로 완전 전환됨 | **BREAKING** |
| V27 | `V27__bbs_master_soft_delete.sql` | bbs_master 소프트 삭제 지원: `deleted_at TIMESTAMPTZ` 컬럼 추가 + `idx_bbs_master_active` 부분 인덱스 (deleted_at IS NULL) | No |
| V28 | `V28__ai_prediction_log.sql` | **AI 도메인**: `ai_prediction_log` 생성 — ML 추론 입력/출력/지연/상태 전체 적재 (GROWTH_STAGE/RISK_SCORE/SIMULATION, 드리프트 분석 기반) | No |
| V29 | `V29__ai_simulation_session.sql` | **AI 도메인**: `ai_simulation_session` 생성 — 익명 시뮬레이션 세션. `client_ip_hash` SHA-256 저장(평문 IP 미저장), 24시간 TTL (`expires_at` DEFAULT) | No |
| V30 | `V30__ai_model_metric.sql` | **AI 도메인**: `ai_model_metric` 생성 — 모델/예측유형/집계주기/기간 UNIQUE upsert. RMSE/MAE/Accuracy/레이턴시 P50·P95·P99 + 드리프트 감지 플래그 | No |
| V31 | `V31__ai_retrain_queue.sql` | **AI 도메인**: `ai_retrain_queue` 생성 — 드리프트 자동/수동 재학습 큐 (QUEUED→ACKNOWLEDGED→IN_PROGRESS→DONE/CANCELED) | No |
| V32 | `V32__create_ai_policy_recommendation_log.sql` | **AI 도메인 (SPEC-CMS-AI-002)**: `ai_policy_recommendation_log` 생성 — PII 제외 정책 추천/피드백 로그. `session_ref` SHA-256 해시 전용, `company_profile` PII 화이트리스트(업종/규모/성장단계/지역) 한정 | No |
| V33 | `V33__ai_rag_query_log_and_policy_embedding.sql` | **AI 도메인 (SPEC-CMS-AI-003)**: `CREATE EXTENSION IF NOT EXISTS vector` (pgvector) 활성화; `policy_program`에 `embed_vector vector(384)`, `embedded_at`, `embed_model_version` 추가 + IVFFlat cosine 인덱스; `ai_rag_query_log` 생성 — RAG 질의/피드백 로그(SHA-256 해시만 저장) | No |

---

## Pending Migrations

현재 적용 대기 중인 마이그레이션 없음. (V33이 최신 적용 버전)

| Filename | Created At | Description | Blocking? |
|----------|-----------|-------------|-----------|
| — | — | — | — |

---

## Rollback Notes

> **V26 이전** 마이그레이션은 모두 가역적(표준 역마이그레이션 적용 가능).
> V26 이후는 데이터 복구를 위한 별도 백업 또는 PiiEmailMigrationJob 재실행이 필요.
> V33은 `CREATE EXTENSION vector`를 포함하므로 pgvector가 미설치된 환경에서는 롤백 전 `DROP EXTENSION IF EXISTS vector`가 필요할 수 있음.

| Migration | Risk Level | Rollback Steps | Data Loss? |
|-----------|-----------|----------------|------------|
| V2 | Low | DROP TABLE token_blacklist, refresh_tokens, login_history, password_history, user_roles, roles, users | No (테스트 환경) |
| V3 | Low | DROP TABLE audit_log (트리거 포함) | No |
| V4 | Low | DELETE FROM user_roles/users WHERE username='admin' | No |
| V5 | Low | ALTER TABLE users DROP COLUMN organization_id; DROP TABLE organization_history, organization | No |
| V6 | Low | DROP TABLE role_permissions, permissions | No |
| V7 | Low | DROP TABLE permission_change_history (트리거 포함) | No |
| V8 | Low | DROP TABLE verification_history, verification_request | No |
| V9 | Low | DROP TABLE personal_data_access_log_archive, personal_data_access_log (APPEND-ONLY 트리거 포함) | No |
| V10 | Low | DROP TABLE bbs_view_log, bbs_post_history, qna, faq, bbs_attachment, bbs_comment, bbs_post, bbs_master | No |
| V12 | Low | DROP TABLE media_processing_job, media_collection_item, media_collection, media_asset_usage, media_asset | No |
| V13 | Low | DROP TABLE seo_redirect, i18n_resource, banner, popup, page_history, content_block, page, template, menu_permissions, menu, site | No |
| V14 | Medium | DROP TABLE maintenance, system_setting, code, code_group, access_stat_monthly, access_stat_daily; DROP TABLE access_log (파티션 테이블) | No — 파티션 DROP 주의 |
| V15 | Low | DROP TABLE safety_check_result, safety_checklist_item, safety_guideline_report, ... (9개 테이블) | No |
| V16 | Low | DROP TABLE policy_application_log, ... (12개 테이블). stub 테이블(departments, notification_template)도 포함 | No |
| V17 | Low | DROP TABLE export_history, chart_dataset_cache, saved_view, dashboard_layout_widget, dashboard_layout, dashboard_widget, kpi_value_history, kpi_value, kpi_definition | No |
| V18 | Low | DROP TABLE recovery_drill_log, data_quality_report, data_quality_rule, 통계 테이블 6종, batch_execution_log, retention_policy, data_dictionary_history, data_dictionary | No |
| V19 | Low | DROP TABLE publication_zip_archive, publication_download_stat, bbs_post_publication_meta, publication_category | No |
| V20 | Low | DROP TABLE survey_answer, survey_response, survey_question, survey | No |
| V21 | Low | DROP TABLE qna_notification_log, qna_notification_optout | No |
| V22 | Low | DROP TABLE search_synonym, search_popular_cache, search_log | No |
| V23 | Low | DROP INDEX idx_page_title_trgm, idx_policy_program_name_trgm, idx_policy_program_desc_html_trgm, idx_media_asset_filename_trgm, idx_media_asset_description_trgm, idx_users_username_trgm, idx_users_name_trgm | No |
| V24 | Medium | ALTER TABLE users DROP COLUMN email_key_version, email_hmac, email_tag, email_iv, email_encrypted; ALTER TABLE users ALTER COLUMN email SET NOT NULL; DROP INDEX idx_users_email_hmac. **주의**: PiiEmailMigrationJob 실행 전 롤백 시 email 데이터는 원래대로 존재함 | No (email 컬럼 아직 존재) |
| V25 | Low | DROP TABLE pii_key_rotation_log | No |
| **V26** | **CRITICAL — 비가역** | **롤백 불가**: `users.email` 컬럼이 영구 삭제됨. 롤백을 위해서는 V26 적용 직전 PostgreSQL 전체 백업(pg_dump)에서 복원하거나, email_encrypted 컬럼의 데이터를 복호화하여 재삽입하는 별도 스크립트 실행 필요. **data_dictionary email 행은 status=REMOVED로 갱신됨 — 별도 UPDATE로 복구 가능**. UserMapper.xml 및 UserServiceImpl의 코드 패치 동시 롤백 필요 | **YES — 이메일 평문 영구 손실 (암호화본은 email_encrypted에 보존됨)** |
| V27 | Low | `ALTER TABLE bbs_master DROP COLUMN deleted_at; DROP INDEX idx_bbs_master_active` | No |
| V28 | Low | `DROP TABLE ai_prediction_log` | No |
| V29 | Low | `DROP TABLE ai_simulation_session` | No |
| V30 | Low | `DROP TABLE ai_model_metric` | No |
| V31 | Low | `DROP TABLE ai_retrain_queue` | No |
| V32 | Low | `DROP TABLE ai_policy_recommendation_log` | No |
| V33 | Medium | `DROP TABLE ai_rag_query_log; ALTER TABLE policy_program DROP COLUMN embed_model_version, DROP COLUMN embedded_at, DROP COLUMN embed_vector; DROP EXTENSION IF EXISTS vector` — **주의**: pgvector 확장 DROP은 벡터 타입을 사용하는 다른 컬럼이 없을 때만 안전 | No |

---

## Security Migration Notes

### V24 — PII 이메일 암호화 (SPEC-CMS-SECURITY-PII-001)

- **목적**: GDPR/개인정보보호법 대응 — 이메일 평문 저장 제거
- **암호화 방식**: AES-256-GCM (email_encrypted + email_iv + email_tag)
- **검색 키**: HMAC-SHA256(hmacKey, normalizedEmail) → email_hmac (UNIQUE 부분인덱스)
- **키 버전**: email_key_version (점진적 키 회전 지원)
- **적용 이전**: PiiEmailMigrationJob 배치 실행으로 기존 rows 암호화 완료 필요
- **사전 조건**: 모든 rows의 email_encrypted + email_hmac이 채워진 후 V26 적용 가능

### V25 — PII 키 회전 로그 (SPEC-CMS-SECURITY-PII-ROTATION-001)

- **목적**: 6개월 주기 암호화 키 회전 배치(@Scheduled)의 실행 이력 추적
- **상태 흐름**: IN_PROGRESS → COMPLETED / FAILED
- **청크 커밋**: 부분 실패 시에도 이미 재암호화된 rows 수(migrated_rows) 보존

### V26 — 평문 이메일 컬럼 DROP (SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-DROP)

- **목적**: email_encrypted/email_hmac 경로로 완전 전환 완료
- **비가역성**: ALTER TABLE users DROP COLUMN email — 복원 불가
- **코드 동시 패치**: UserMapper.xml email 컬럼 매핑 제거, UserServiceImpl existsByEmail → existsByEmailHmac 전환
- **운영 주의**: V26 적용 전 반드시 pg_dump 전체 백업 수행

### V29 — AI 시뮬레이션 세션 익명화 (SPEC-CMS-AI-001)

- **목적**: 익명 사용자 시뮬레이션 세션. 평문 IP 절대 미저장.
- **client_ip_hash**: SHA-256(raw IP) — 64자 hex, IpHashUtil 재사용
- **TTL**: `expires_at DEFAULT now() + INTERVAL '24 hours'` — 배치 정리 대상
- **보안**: AI 요청에서 PII(대표자명·식별번호) 제외, 재무지표·업종·연도만 허용

### V32 — AI 정책 추천 로그 익명화 (SPEC-CMS-AI-002)

- **목적**: 정책 추천 이력 적재. PII 완전 배제.
- **session_ref**: 익명 세션 또는 회원ID의 SHA-256 해시 (평문 미저장)
- **company_profile**: PII 화이트리스트 — `ksic_code`, `employee_count`, `growth_stage`, `region_code`, `annual_revenue` 5개 필드만 허용 (대표자명·법인식별번호 금지)
- **interaction_type**: VIEWED(policy_id=NULL) / CLICKED·APPLIED·DISMISSED(policy_id 필수) — CHECK 제약 강제

### V33 — RAG 질의 로그 익명화 + pgvector (SPEC-CMS-AI-003)

- **목적**: RAG 응답 품질 측정 및 피드백 수집. 질문 평문 미저장.
- **question_hash**: 질문 텍스트 SHA-256 해시 — ragQueryCache 키와 동일 산식
- **session_ref**: V32와 동일 규칙 (SHA-256 해시 전용)
- **query_ref**: 클라이언트 반환 UUID — 피드백(feedback/feedback_at) 비동기 갱신의 상관키
- **pgvector 요구사항**: `pgvector/pgvector:pg16` 이미지 필수 (`postgres:16-alpine` 미사용)
- **embed_vector**: `vector(384)` — sentence embedding 384차원, IVFFlat cosine 인덱스 (lists=100)
