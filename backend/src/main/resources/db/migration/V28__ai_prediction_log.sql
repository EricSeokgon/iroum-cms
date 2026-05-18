-- SPEC-CMS-AI-001 Step 1 — AI 예측 로그
-- 모든 ML 추론 호출의 입력/출력/지연/상태를 적재 (모니터링·드리프트 분석 기반)
CREATE TABLE ai_prediction_log (
    id BIGSERIAL PRIMARY KEY,
    prediction_type VARCHAR(20) NOT NULL CHECK (prediction_type IN ('GROWTH_STAGE','RISK_SCORE','SIMULATION')),
    model_name VARCHAR(100) NOT NULL,
    model_version VARCHAR(20) NOT NULL,
    request_ref VARCHAR(100),
    input_features JSONB NOT NULL,
    output_result JSONB,
    confidence NUMERIC(5,4),
    latency_ms INTEGER,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS','ML_ERROR','TIMEOUT','FALLBACK')),
    actual_value JSONB,
    predicted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    labeled_at TIMESTAMPTZ
);
CREATE INDEX idx_ai_prediction_log_type ON ai_prediction_log(prediction_type);
CREATE INDEX idx_ai_prediction_log_status ON ai_prediction_log(status);
CREATE INDEX idx_ai_prediction_log_predicted_at ON ai_prediction_log(predicted_at DESC);
