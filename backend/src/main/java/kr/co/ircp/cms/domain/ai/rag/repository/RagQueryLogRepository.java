package kr.co.ircp.cms.domain.ai.rag.repository;

import kr.co.ircp.cms.domain.ai.rag.dto.RagMetricsAggregate;
import kr.co.ircp.cms.domain.ai.rag.dto.RagMetricsQuery;
import kr.co.ircp.cms.domain.ai.rag.dto.RagTimeSeriesRow;
import kr.co.ircp.cms.domain.ai.rag.entity.AiRagQueryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RAG 질의/피드백 로그 매퍼.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-013/014/015 — 질의 로그 적재, 피드백 멱등 갱신,
 * 관리자 메트릭 집계. JSONB는 {@code ::jsonb} 캐스팅(AI-002 패턴).
 */
@Mapper
public interface RagQueryLogRepository {

    /** 질의 로그 1행 적재 (REQ-RAG-014, 비동기 호출). */
    int insertLog(AiRagQueryLog entity);

    /**
     * queryRef로 feedback·feedback_at 멱등 갱신 (REQ-RAG-013, AC-RAG-004).
     *
     * @return 갱신된 행 수 (0이면 미존재 — 비동기 적재 지연)
     */
    int updateFeedback(@Param("queryRef") String queryRef,
                       @Param("feedback") String feedback);

    /** 관리자 메트릭 집계 원시 행 (REQ-RAG-015). */
    RagMetricsAggregate aggregateMetrics(RagMetricsQuery query);

    /** 일자별 시계열 원시 행 (REQ-RAG-015). */
    List<RagTimeSeriesRow> timeSeries(RagMetricsQuery query);
}
