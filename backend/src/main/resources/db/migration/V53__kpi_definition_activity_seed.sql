-- SPEC-CMS-KPI-002: 운영 활동 지표 KPI 4종(코드 5개) 시드.
--
-- 부모 SPEC-CMS-KPI-001 이 구축한 kpi_definition / kpi_value(V17) / kpi_aggregation_mv(V45)
-- 인프라 위에 access_log 단일 원천 기반 운영 활동 지표를 추가한다.
--
-- [HARD] INSERT 전용. 신규 테이블/컬럼/구조 DDL/MV 변경 금지(SPEC §4.4).
-- calculation_query 는 NOT NULL 이므로 산식 설명 문자열을 적재한다(V45 시드 패턴 계승).
-- refresh_interval_min: 일별=1440, 월별(MAU)=43200.

INSERT INTO kpi_definition (code, name, description, calculation_query, refresh_interval_min, status)
VALUES
    ('DAU',
     '일 활성 사용자',
     '일자별 access_log 의 고유 로그인 사용자 수(COUNT DISTINCT user_id, NULL 제외)',
     'COUNT(DISTINCT user_id) FROM access_log WHERE user_id IS NOT NULL (daily)',
     1440, 'ACTIVE'),
    ('MAU',
     '월 활성 사용자',
     '월별 access_log 의 고유 로그인 사용자 수(COUNT DISTINCT user_id, NULL 제외)',
     'COUNT(DISTINCT user_id) FROM access_log WHERE user_id IS NOT NULL (monthly)',
     43200, 'ACTIVE'),
    ('CONTENT_VIEW',
     '콘텐츠 조회 수',
     '일자별 page_url 패턴 분류(notice/post/publication) 유형별 조회 건수',
     'COUNT(*) GROUP BY contentType FROM access_log (daily)',
     1440, 'ACTIVE'),
    ('AVG_SESSION_DURATION',
     '평균 세션 지속 시간(초)',
     '일자별 session_id 별 (MAX-MIN) created_at 의 전체 평균(초). 30분 idle gap 세션 경계 분리',
     'AVG(EXTRACT(EPOCH FROM (MAX(created_at)-MIN(created_at)))) per session (daily)',
     1440, 'ACTIVE'),
    ('API_ERROR_RATE',
     'API 오류 응답 비율(%)',
     '일자별 status_code>=500 비율(%). COUNT(status_code>=500)/NULLIF(COUNT(*),0)*100',
     'COUNT(*) FILTER (WHERE status_code>=500) / NULLIF(COUNT(*),0) * 100 (daily)',
     1440, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;
