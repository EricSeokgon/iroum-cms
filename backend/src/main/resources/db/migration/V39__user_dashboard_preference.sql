-- SPEC-CMS-DASHBOARD-PERSONALIZE-001 §5.1
-- 사용자별 대시보드 개인화 환경설정 (1:1, PK = user_id)
--
-- 격차 채움 대상:
--   1) 위젯 가시성 비파괴 토글 (hidden_widget_instance_ids JSONB)
--   2) 시각 환경설정 (theme/density/font_scale/color_palette_preference/sidebar_collapsed)
--   3) 다중 세션 충돌 감지를 위한 schema_version + updated_at
--
-- 후행 SPEC-CMS-008 의 dashboard_layout / dashboard_layout_widget DDL 변경 없음.

CREATE TABLE user_dashboard_preference (
    user_id                          BIGINT       PRIMARY KEY
                                     REFERENCES users(id) ON DELETE CASCADE,

    -- 위젯 가시성 (레이아웃 비파괴 숨김)
    -- 형식: {"{layout_id}": ["{instance_id_1}", "{instance_id_2}"]}
    -- 예: {"12": ["w-pv-001", "w-policy-cvr-003"]}
    hidden_widget_instance_ids       JSONB        NOT NULL DEFAULT '{}'::jsonb,

    -- 시각 테마
    theme                            VARCHAR(16)  NOT NULL DEFAULT 'SYSTEM'
        CHECK (theme IN ('LIGHT','DARK','SYSTEM')),

    -- 밀도 / 폰트 배율
    density                          VARCHAR(16)  NOT NULL DEFAULT 'NORMAL'
        CHECK (density IN ('COMPACT','NORMAL','COMFORTABLE')),
    font_scale                       NUMERIC(3,2) NOT NULL DEFAULT 1.00
        CHECK (font_scale IN (0.875, 1.00, 1.125)),

    -- 색상 팔레트 전역 선호 (위젯별 설정보다 우선)
    color_palette_preference         VARCHAR(16)  NOT NULL DEFAULT 'DEFAULT'
        CHECK (color_palette_preference IN ('DEFAULT','COLORBLIND','MONOCHROME')),

    -- UI 상태 (사이드바, 펼침/접힘)
    sidebar_collapsed                BOOLEAN      NOT NULL DEFAULT FALSE,

    -- 메타
    schema_version                   SMALLINT     NOT NULL DEFAULT 1,
    created_at                       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_pref_updated ON user_dashboard_preference(updated_at DESC);

COMMENT ON TABLE user_dashboard_preference IS
  'SPEC-CMS-DASHBOARD-PERSONALIZE-001: 사용자별 대시보드 개인화 환경설정 (1:1)';
COMMENT ON COLUMN user_dashboard_preference.hidden_widget_instance_ids IS
  'layout_id 별 숨김 위젯 instance_id 목록. 삭제가 아닌 비파괴 토글.';
COMMENT ON COLUMN user_dashboard_preference.color_palette_preference IS
  'dashboard_widget.default_config.color_palette 보다 우선 적용되는 사용자 전역 팔레트';
COMMENT ON COLUMN user_dashboard_preference.schema_version IS
  '환경설정 스키마 버전. 향후 컬럼 추가 시 lazy migration 의 분기 기준.';
