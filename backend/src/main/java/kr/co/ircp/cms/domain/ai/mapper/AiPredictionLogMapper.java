package kr.co.ircp.cms.domain.ai.mapper;

import kr.co.ircp.cms.domain.ai.model.AiPredictionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * AI 예측 로그 MyBatis 매퍼.
 * SPEC-CMS-AI-001
 */
// @MX:ANCHOR: [AUTO] AiPredictionLogMapper — ML 추론 호출 적재 진입점
// @MX:REASON: GrowthStage/RiskScore/Simulation 서비스 + 메트릭 집계 배치에서 공통 사용 예정 (fan_in >= 3)
// @MX:SPEC: SPEC-CMS-AI-001
@Mapper
public interface AiPredictionLogMapper {

    void insert(AiPredictionLog log);

    Optional<AiPredictionLog> findById(@Param("id") Long id);

    List<AiPredictionLog> findByPredictionType(
            @Param("predictionType") String predictionType,
            @Param("limit") int limit);

    int updateStatus(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("actualValue") String actualValue);
}
