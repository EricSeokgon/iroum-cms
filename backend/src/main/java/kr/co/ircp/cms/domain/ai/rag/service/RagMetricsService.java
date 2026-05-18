package kr.co.ircp.cms.domain.ai.rag.service;

import kr.co.ircp.cms.domain.ai.rag.dto.RagMetricsQuery;
import kr.co.ircp.cms.domain.ai.rag.dto.RagMetricsResponse;

/**
 * RAG 품질 모니터링 서비스.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-015 — 만족도·캐시 히트율·평균 응답시간·degraded
 * 비율·시계열을 집계한다. 0-나눗셈은 0.0으로 가드한다.
 */
public interface RagMetricsService {

    RagMetricsResponse getMetrics(RagMetricsQuery query);
}
