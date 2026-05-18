-- SPEC-CMS-AI-001 Step 1 — AI 재학습 큐
-- 드리프트 자동 트리거 또는 수동 요청으로 모델 재학습 작업을 큐잉.
CREATE TABLE ai_retrain_queue (
    id BIGSERIAL PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    trigger_reason VARCHAR(30) NOT NULL CHECK (trigger_reason IN ('DRIFT_ACCURACY','DRIFT_ERROR','MANUAL')),
    trigger_detail JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','ACKNOWLEDGED','IN_PROGRESS','DONE','CANCELED')),
    requested_by BIGINT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_retrain_queue_status ON ai_retrain_queue(status, requested_at DESC);
