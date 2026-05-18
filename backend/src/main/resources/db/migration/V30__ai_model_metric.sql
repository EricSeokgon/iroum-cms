-- SPEC-CMS-AI-001 Step 1 — AI 모델 성능 지표 집계
-- 모델/예측유형/집계주기/기간 단위 UNIQUE upsert. 드리프트 감지 플래그 포함.
CREATE TABLE ai_model_metric (
    id BIGSERIAL PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    prediction_type VARCHAR(20) NOT NULL CHECK (prediction_type IN ('GROWTH_STAGE','RISK_SCORE','SIMULATION')),
    aggregate_period VARCHAR(10) NOT NULL CHECK (aggregate_period IN ('DAILY','WEEKLY','MONTHLY')),
    period_start DATE NOT NULL,
    rmse NUMERIC(10,4),
    mae NUMERIC(10,4),
    accuracy NUMERIC(5,4),
    latency_p50 INTEGER,
    latency_p95 INTEGER,
    latency_p99 INTEGER,
    sample_count INTEGER NOT NULL DEFAULT 0,
    drift_detected BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_model_metric UNIQUE (model_name, prediction_type, aggregate_period, period_start)
);
CREATE INDEX idx_ai_model_metric_drift ON ai_model_metric(drift_detected, created_at DESC);
