-- iroum-cms 초기 baseline 마이그레이션 (Step 0 bootstrap)
-- 도메인 테이블은 SPEC-CMS-002 구현 단계에서 V2부터 추가된다.

-- pgcrypto: UUID 생성, 암호화 해시 함수 지원
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- pg_trgm: 한국어 LIKE 검색 성능 향상 (GIN 인덱스 연동)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 타임존 확인 코멘트 (운영 서버는 UTC 기준, 애플리케이션이 Asia/Seoul로 변환)
COMMENT ON SCHEMA public IS 'iroum-cms schema (baseline V1)';
