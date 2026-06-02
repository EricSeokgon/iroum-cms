-- SPEC-CMS-DASHBOARD-REFRESH-001: 사용자별 대시보드 자동 새로고침 주기
ALTER TABLE user_dashboard_preference
    ADD COLUMN refresh_interval_seconds INT DEFAULT NULL
        CHECK (refresh_interval_seconds IN (30, 60, 300, 900, 1800));

COMMENT ON COLUMN user_dashboard_preference.refresh_interval_seconds IS
  'SPEC-CMS-DASHBOARD-REFRESH-001: 대시보드 자동 새로고침 주기(초). NULL = OFF. 허용값 30/60/300/900/1800.';
