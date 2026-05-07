-- SPEC-CMS-010 v0.2: 통합 검색 ILIKE fallback 성능 보강 (코드 리뷰 #2)
-- 의존: V1 (pg_trgm 확장), V12 (media_asset), V13 (page), V16 (policy_program)
-- REQ: REQ-SEARCH-001, REQ-SEARCH-004 — 대용량 테이블 ILIKE 시 Full Table Scan 방지
--
-- 배경:
--   UnifiedSearchMapper.xml 의 content / policy / media 도메인은 search_vector 미존재로
--   ILIKE '%query%' fallback 을 사용한다. trgm GIN 인덱스가 없으면 행 수 증가에 따라
--   응답시간이 선형으로 악화되어 SPEC §6 NFR(P95 <= 800ms)을 위반한다.
--
-- 적용 효과:
--   - pg_trgm 의 gin_trgm_ops 가 ILIKE/LIKE 양방향 와일드카드 ('%abc%') 패턴을
--     trigram 매칭으로 가속한다.
--   - 인덱스가 작동하지 않는 케이스 (1자 미만 입력 등)는 PostgreSQL 이 자동으로
--     seq scan 으로 fallback 한다.
--
-- 주의:
--   - GIN 인덱스 빌드는 데이터 양에 비례하므로 운영 적용 시 CONCURRENTLY 옵션
--     사용을 검토하라 (Flyway 는 트랜잭션 내 실행이므로 본 스크립트는 단순 CREATE).
--   - PostgreSQL 의 IF NOT EXISTS 는 동일 인덱스명 중복 생성을 방어한다.
--   - email/email_hash 컬럼은 PII 보호 정책상 trgm 인덱스를 적용하지 않는다 (REQ-CROSS-002).

-- ─── pg_trgm 확장 보장 (V1 에 이미 존재하지만 방어적으로 IF NOT EXISTS) ──────
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ─── content 도메인 (page) ────────────────────────────────────────────────
-- UnifiedSearchMapper.xml contentSearchSql / countUnified content 분기에서
-- p.title ILIKE '%query%' 를 사용한다.
CREATE INDEX IF NOT EXISTS idx_page_title_trgm
    ON page USING GIN (title gin_trgm_ops);

COMMENT ON INDEX idx_page_title_trgm IS
    'SPEC-CMS-010 ILIKE fallback 가속 (코드 리뷰 #2) — UnifiedSearchMapper contentSearchSql';

-- ─── policy 도메인 (policy_program) ────────────────────────────────────────
-- UnifiedSearchMapper.xml policySearchSql / countUnified policy 분기에서
-- pp.program_name / pp.description_html 을 ILIKE 로 검색한다.
CREATE INDEX IF NOT EXISTS idx_policy_program_name_trgm
    ON policy_program USING GIN (program_name gin_trgm_ops);

COMMENT ON INDEX idx_policy_program_name_trgm IS
    'SPEC-CMS-010 ILIKE fallback 가속 (코드 리뷰 #2) — UnifiedSearchMapper policySearchSql.program_name';

CREATE INDEX IF NOT EXISTS idx_policy_program_desc_html_trgm
    ON policy_program USING GIN (description_html gin_trgm_ops);

COMMENT ON INDEX idx_policy_program_desc_html_trgm IS
    'SPEC-CMS-010 ILIKE fallback 가속 (코드 리뷰 #2) — UnifiedSearchMapper policySearchSql.description_html';

-- ─── media 도메인 (media_asset) ────────────────────────────────────────────
-- UnifiedSearchMapper.xml mediaSearchSql / countUnified media 분기에서
-- ma.original_filename / ma.description 을 ILIKE 로 검색한다.
CREATE INDEX IF NOT EXISTS idx_media_asset_filename_trgm
    ON media_asset USING GIN (original_filename gin_trgm_ops);

COMMENT ON INDEX idx_media_asset_filename_trgm IS
    'SPEC-CMS-010 ILIKE fallback 가속 (코드 리뷰 #2) — UnifiedSearchMapper mediaSearchSql.original_filename';

CREATE INDEX IF NOT EXISTS idx_media_asset_description_trgm
    ON media_asset USING GIN (description gin_trgm_ops);

COMMENT ON INDEX idx_media_asset_description_trgm IS
    'SPEC-CMS-010 ILIKE fallback 가속 (코드 리뷰 #2) — UnifiedSearchMapper mediaSearchSql.description';

-- ─── users (관리자 사용자 검색) ─────────────────────────────────────────────
-- UserMapper.xml findPage / countAll / findPageWithScope / countAllWithScope 에서
-- username / name 을 ILIKE 로 검색한다. email 은 PII 보호상 제외.
CREATE INDEX IF NOT EXISTS idx_users_username_trgm
    ON users USING GIN (username gin_trgm_ops);

COMMENT ON INDEX idx_users_username_trgm IS
    'SPEC-CMS-002 사용자 검색 ILIKE 가속 (코드 리뷰 #2) — UserMapper findPage.username';

CREATE INDEX IF NOT EXISTS idx_users_name_trgm
    ON users USING GIN (name gin_trgm_ops);

COMMENT ON INDEX idx_users_name_trgm IS
    'SPEC-CMS-002 사용자 검색 ILIKE 가속 (코드 리뷰 #2) — UserMapper findPage.name';
