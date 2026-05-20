-- iroum-cms 샘플 데이터 Seed Script
-- 실행: docker exec -i iroum-postgres psql -U iroum_cms -d iroum_cms_local < deploy/seed_sample_data.sql

BEGIN;

-- ──────────────────────────────────────────
-- 1. 추가 사용자 계정 (EDITOR, VIEWER, DEPT_ADMIN)
-- ──────────────────────────────────────────
INSERT INTO users (username, password_hash, name, status, password_changed_at, created_at, updated_at)
VALUES
  ('editor01', crypt('Editor1234!', gen_salt('bf', 12)), '홍편집자',   'ACTIVE', NOW(), NOW(), NOW()),
  ('viewer01', crypt('Viewer1234!', gen_salt('bf', 12)), '김조회자',   'ACTIVE', NOW(), NOW(), NOW()),
  ('dept01',   crypt('Dept1234!',   gen_salt('bf', 12)), '박부서장',   'ACTIVE', NOW(), NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- 역할 할당
INSERT INTO user_roles (user_id, role_code, granted_at, granted_by)
SELECT u.id, r.role_code, NOW(), admin.id
FROM (VALUES
  ('editor01', 'EDITOR'),
  ('viewer01',  'VIEWER'),
  ('dept01',    'DEPT_ADMIN')
) AS r(username, role_code)
JOIN users u ON u.username = r.username
CROSS JOIN (SELECT id FROM users WHERE username = 'admin') admin
ON CONFLICT (user_id, role_code) DO NOTHING;

-- ──────────────────────────────────────────
-- 2. 정책 프로그램 15건
-- ──────────────────────────────────────────
INSERT INTO policy_program (
  code, ministry, program_name, program_name_i18n, description_html,
  target_industries, target_regions,
  min_employees, max_employees, min_revenue, max_revenue,
  min_business_age_months, max_business_age_months,
  application_start, application_end,
  budget_total, budget_per_company,
  source_url, status
) VALUES
(
  'MSS-2026-001', '중소벤처기업부', '스마트공장 구축지원 사업',
  '{"en": "Smart Factory Construction Support"}',
  '<p>중소기업의 제조 경쟁력 강화를 위해 스마트공장 구축 비용을 지원합니다.</p>',
  ARRAY['C25','C26','C27','C28','C29'], ARRAY['21','22','23','24','25','26','27','28','29','30'],
  10, 300, NULL, 30000000000,
  0, NULL,
  '2026-03-01 00:00:00+00', '2026-11-30 00:00:00+00',
  100000000000, 500000000,
  'https://www.mss.go.kr', 'ACTIVE'
),
(
  'MSS-2026-002', '중소벤처기업부', '창업 초기 기업 지원 (창업패키지)',
  '{"en": "Early-Stage Startup Support Package"}',
  '<p>창업 3년 이내 기업에 사업화 자금 및 멘토링을 지원합니다.</p>',
  ARRAY['J58','J59','J60','J61','J62','J63'], ARRAY['11','41','42','43','44','45'],
  1, 30, NULL, 1000000000,
  0, 36,
  '2026-01-15 00:00:00+00', '2026-06-30 00:00:00+00',
  50000000000, 100000000,
  'https://www.k-startup.go.kr', 'ACTIVE'
),
(
  'MOTIE-2026-001', '산업통상자원부', '탄소중립 산업공정 혁신 지원',
  '{"en": "Carbon-Neutral Industrial Process Innovation"}',
  '<p>온실가스 감축을 위한 공정 혁신 R&D 및 설비 투자를 지원합니다.</p>',
  ARRAY['C10','C11','C13','C14','C15','C16','C17','C18','C19','C20'], ARRAY['11','21','22','23','24','25'],
  50, NULL, NULL, NULL,
  60, NULL,
  '2026-02-01 00:00:00+00', '2026-09-30 00:00:00+00',
  200000000000, 2000000000,
  'https://www.motie.go.kr', 'ACTIVE'
),
(
  'MOEL-2026-001', '고용노동부', '청년 고용 촉진 장려금',
  '{"en": "Youth Employment Promotion Incentive"}',
  '<p>만 15~34세 청년을 정규직으로 채용하는 기업에 인건비를 지원합니다.</p>',
  ARRAY['C','G','H','I','J','M','N'], ARRAY['11','21','22','23','24','25','26','27','28','29','30','31','32','33','34','35','36','37','38','39','40','41','42','43','44','45'],
  NULL, 500, NULL, NULL,
  0, NULL,
  '2026-01-01 00:00:00+00', '2026-12-31 00:00:00+00',
  300000000000, 6000000,
  'https://www.moel.go.kr', 'ACTIVE'
),
(
  'MSIT-2026-001', '과학기술정보통신부', 'AI·빅데이터 전문기업 육성',
  '{"en": "AI and Big Data Company Cultivation"}',
  '<p>인공지능·빅데이터 기반 제품·서비스 개발 기업을 선정하여 사업화를 지원합니다.</p>',
  ARRAY['J58','J59','J60','J61','J62','J63','M70','M72','M73'], ARRAY['11','41','42','43','44','45'],
  1, 50, NULL, 5000000000,
  0, 60,
  '2026-03-15 00:00:00+00', '2026-08-31 00:00:00+00',
  80000000000, 300000000,
  'https://www.msit.go.kr', 'ACTIVE'
),
(
  'MAFRA-2026-001', '농림축산식품부', '농식품 스타트업 육성 지원',
  '{"en": "Agri-Food Startup Cultivation Support"}',
  '<p>농식품 혁신 창업기업의 기술개발 및 시장진출을 지원합니다.</p>',
  ARRAY['A01','A02','C10','C11','G47'], ARRAY['33','34','35','36','37','38','39','40','42','43','44','45'],
  1, 50, NULL, 2000000000,
  0, 48,
  '2026-04-01 00:00:00+00', '2026-10-31 00:00:00+00',
  30000000000, 200000000,
  'https://www.mafra.go.kr', 'ACTIVE'
),
(
  'MSS-2026-003', '중소벤처기업부', '수출 유망 중소기업 지원',
  '{"en": "Promising SME Export Support"}',
  '<p>해외 시장 개척을 희망하는 중소기업에 마케팅·인증·물류 비용을 지원합니다.</p>',
  ARRAY['C','G'], ARRAY['11','21','22','23','24','25','26','27','28','29','30','31','32'],
  10, 200, 1000000000, 50000000000,
  24, NULL,
  '2026-02-15 00:00:00+00', '2026-07-31 00:00:00+00',
  150000000000, 50000000,
  'https://www.mss.go.kr', 'ACTIVE'
),
(
  'MOLIT-2026-001', '국토교통부', '건설기술 혁신 기업 R&D 지원',
  '{"en": "Construction Technology Innovation R&D Support"}',
  '<p>건설산업 디지털전환(BIM·스마트건설) 핵심기술 개발을 지원합니다.</p>',
  ARRAY['F41','F42','F43'], ARRAY['11','21','22','23','24','25','26','27','28','29','30','31','32'],
  20, NULL, NULL, NULL,
  36, NULL,
  '2026-05-01 00:00:00+00', '2026-09-30 00:00:00+00',
  100000000000, 1000000000,
  'https://www.molit.go.kr', 'ACTIVE'
),
(
  'MOF-2026-001', '금융위원회', '혁신금융 핀테크 기업 지원',
  '{"en": "Innovative Finance Fintech Support"}',
  '<p>핀테크·인슈어테크 등 혁신금융 서비스 개발 기업에 특례 규제 샌드박스를 지원합니다.</p>',
  ARRAY['K64','K65','K66','J62'], ARRAY['11','41','42','43','44','45'],
  1, 100, NULL, 10000000000,
  0, NULL,
  '2026-01-10 00:00:00+00', '2026-12-31 00:00:00+00',
  50000000000, 200000000,
  'https://www.fsc.go.kr', 'ACTIVE'
),
(
  'MCST-2026-001', '문화체육관광부', '콘텐츠 산업 글로벌 진출 지원',
  '{"en": "Content Industry Global Expansion Support"}',
  '<p>K-콘텐츠(게임·영상·웹툰) 기업의 해외 진출과 현지화를 지원합니다.</p>',
  ARRAY['J58','J59','J60','R90','R91'], ARRAY['11','21','41','42','43','44','45'],
  5, 100, NULL, NULL,
  12, NULL,
  '2026-03-01 00:00:00+00', '2026-08-31 00:00:00+00',
  60000000000, 150000000,
  'https://www.mcst.go.kr', 'ACTIVE'
),
(
  'KDC-2026-001', '서울특별시', '서울 혁신 창업 지원금',
  '{"en": "Seoul Innovation Startup Grant"}',
  '<p>서울시 소재 창업기업에 초기 운영자금 및 판로개척을 지원합니다.</p>',
  ARRAY['J','M','N'], ARRAY['11'],
  1, 20, NULL, 1000000000,
  0, 36,
  '2026-04-01 00:00:00+00', '2026-10-31 00:00:00+00',
  20000000000, 30000000,
  'https://startup.seoul.go.kr', 'ACTIVE'
),
(
  'GGI-2026-001', '경기도', '경기도 중소기업 ESG 경영 지원',
  '{"en": "Gyeonggi Province SME ESG Management Support"}',
  '<p>ESG 경영 도입·고도화를 추진하는 경기도 중소기업에 컨설팅 및 인증비용을 지원합니다.</p>',
  ARRAY['C','D','E','F','G','H','I','J'], ARRAY['41','42','43','44','45'],
  10, 500, NULL, NULL,
  0, NULL,
  '2026-05-01 00:00:00+00', '2026-11-30 00:00:00+00',
  10000000000, 15000000,
  'https://www.gg.go.kr', 'ACTIVE'
),
(
  'MSS-2026-004', '중소벤처기업부', '소상공인 디지털 전환 지원',
  '{"en": "Small Business Digital Transformation Support"}',
  '<p>소상공인의 온라인 판매채널 구축과 디지털 마케팅을 지원합니다.</p>',
  ARRAY['G47','I55','I56','G45','G46'], ARRAY['11','21','22','23','24','25','26','27','28','29','30','31','32','33','34','35','36','37','38','39','40','41','42','43','44','45'],
  1, 9, NULL, 1000000000,
  0, NULL,
  '2026-01-01 00:00:00+00', '2026-12-31 00:00:00+00',
  200000000000, 10000000,
  'https://www.mss.go.kr', 'ACTIVE'
),
(
  'KOTRA-2026-001', '산업통상자원부', '글로벌 강소기업 해외진출 패키지',
  '{"en": "Global Hidden Champion Overseas Expansion Package"}',
  '<p>수출 실적 보유 중소·중견기업의 글로벌 시장 확대를 위한 통합 지원입니다.</p>',
  ARRAY['C','D','E'], ARRAY['11','21','22','23','24','25','26','27','28','29','30','31','32'],
  30, 1000, 5000000000, NULL,
  36, NULL,
  '2026-02-01 00:00:00+00', '2026-06-30 00:00:00+00',
  180000000000, 100000000,
  'https://www.kotra.or.kr', 'ACTIVE'
),
(
  'MOEL-2026-002', '고용노동부', '장애인 고용장려금',
  '{"en": "Disability Employment Incentive Grant"}',
  '<p>장애인 의무고용률 초과 달성 기업에 인건비 일부를 장려금으로 지원합니다.</p>',
  ARRAY['C','G','H','I','J','M','N'], ARRAY['11','21','22','23','24','25','26','27','28','29','30','31','32','33','34','35','36','37','38','39','40','41','42','43','44','45'],
  NULL, NULL, NULL, NULL,
  0, NULL,
  '2026-01-01 00:00:00+00', '2026-12-31 00:00:00+00',
  NULL, 4000000,
  'https://www.moel.go.kr', 'ACTIVE'
)
ON CONFLICT (code) DO NOTHING;

-- ──────────────────────────────────────────
-- 3. 기업 안전 프로필
-- ──────────────────────────────────────────
INSERT INTO company_safety_profile (
  company_id, industry_code, sub_industry, employee_count,
  primary_process, hazard_factors, risk_score, risk_grade, updated_at
)
VALUES
  (
    (SELECT id FROM users WHERE username = 'admin'),
    'C28', '일반 목적용 기계 제조업', 120,
    '기계 조립 및 용접',
    '[{"type":"화학물질","level":"중"},{"type":"소음","level":"경"},{"type":"고소작업","level":"중"}]'::jsonb,
    55.00, 'C', NOW()
  ),
  (
    (SELECT id FROM users WHERE username = 'ircp'),
    'J62', '컴퓨터 프로그래밍, 시스템 통합 및 관리업', 45,
    '소프트웨어 개발 및 IT 서비스',
    '[{"type":"VDT작업","level":"경"},{"type":"근골격계","level":"경"}]'::jsonb,
    18.00, 'A', NOW()
  )
ON CONFLICT (company_id) DO NOTHING;

-- ──────────────────────────────────────────
-- 4. 기업 매칭 입력
-- ──────────────────────────────────────────
INSERT INTO company_match_input (
  company_id, industry_codes, region_codes, employee_count, annual_revenue,
  business_age_months, certifications, custom_attrs, last_updated_at
)
VALUES
  (
    (SELECT id FROM users WHERE username = 'admin'),
    ARRAY['C28'], ARRAY['22'], 120, 8500000000,
    96, ARRAY['ISO9001','INNO-BIZ'],
    '{"growth_stage":"GROWTH","founded_year":2018}'::jsonb,
    NOW()
  ),
  (
    (SELECT id FROM users WHERE username = 'ircp'),
    ARRAY['J62'], ARRAY['11'], 45, 2300000000,
    48, ARRAY['ISO27001','VENTURE'],
    '{"growth_stage":"STARTUP","founded_year":2022}'::jsonb,
    NOW()
  )
ON CONFLICT (company_id) DO NOTHING;

COMMIT;

-- 결과 요약
SELECT '사용자' AS 유형, COUNT(*) AS 건수 FROM users
UNION ALL
SELECT '정책 프로그램', COUNT(*) FROM policy_program
UNION ALL
SELECT '기업 안전 프로필', COUNT(*) FROM company_safety_profile
UNION ALL
SELECT '기업 매칭 입력', COUNT(*) FROM company_match_input;
