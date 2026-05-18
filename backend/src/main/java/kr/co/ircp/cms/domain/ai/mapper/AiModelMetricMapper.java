package kr.co.ircp.cms.domain.ai.mapper;

import kr.co.ircp.cms.domain.ai.model.AiModelMetric;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * AI 모델 성능 지표 MyBatis 매퍼.
 * SPEC-CMS-AI-001
 */
// @MX:SPEC: SPEC-CMS-AI-001
@Mapper
public interface AiModelMetricMapper {

    /** UNIQUE(model_name, prediction_type, aggregate_period, period_start) upsert. */
    void insertOrUpdate(AiModelMetric metric);

    Optional<AiModelMetric> findByModelAndPeriod(
            @Param("modelName") String modelName,
            @Param("predictionType") String predictionType,
            @Param("aggregatePeriod") String aggregatePeriod,
            @Param("periodStart") LocalDate periodStart);

    List<AiModelMetric> findDriftDetected();

    Optional<AiModelMetric> findLatest(
            @Param("modelName") String modelName,
            @Param("predictionType") String predictionType);
}
