package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.dto.AiDriftAlertDto;
import kr.co.ircp.cms.domain.ai.dto.AiMetricDto;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 모델 성능 지표 집계·드리프트 서비스.
 *
 * <p>SPEC-CMS-AI-001 — 예측 로그 기반 RMSE/MAE/accuracy 산출 + 드리프트 자동 탐지.
 */
public interface AiModelMetricService {

    /**
     * 예측유형별로 지정 일자의 지표를 집계하고, 임계 위반 시 드리프트 처리한다.
     *
     * @param predictionType GROWTH_STAGE / RISK_SCORE / SIMULATION
     * @param periodStart    집계 기준 일자 (보통 어제)
     */
    void aggregate(String predictionType, LocalDate periodStart);

    /** 운영자용 최근 지표 목록 조회. */
    List<AiMetricDto> findMetrics(String predictionType, int limit);

    /** 드리프트 감지된 지표를 경보 형태로 조회. */
    List<AiDriftAlertDto> findDriftAlerts();
}
