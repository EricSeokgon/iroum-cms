package kr.co.ircp.cms.domain.ai.mapper;

import kr.co.ircp.cms.domain.ai.model.AiModelMetric;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-AI-001 Step 1 — ai_model_metric MyBatis 매퍼 RED 테스트.
 *
 * <p>UNIQUE (model_name, prediction_type, aggregate_period, period_start) 제약,
 * insertOrUpdate upsert, 드리프트 조회를 검증한다.
 */
// @MX:SPEC: SPEC-CMS-AI-001
@DisplayName("AiModelMetricMapper IT (SPEC-CMS-AI-001)")
class AiModelMetricMapperTest extends AbstractIntegrationTest {

    @Autowired AiModelMetricMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM ai_model_metric");
    }

    @Test
    @DisplayName("metric을 삽입하고 findByModelAndPeriod로 조회한다")
    void insertAndFindByModelAndPeriod() {
        LocalDate periodStart = LocalDate.of(2026, 5, 18);
        AiModelMetric metric = AiModelMetric.builder()
                .modelName("growth-stage-clf")
                .predictionType("GROWTH_STAGE")
                .aggregatePeriod("DAILY")
                .periodStart(periodStart)
                .rmse(new BigDecimal("0.1234"))
                .mae(new BigDecimal("0.0987"))
                .accuracy(new BigDecimal("0.9210"))
                .latencyP50(120)
                .latencyP95(310)
                .latencyP99(490)
                .sampleCount(1500)
                .driftDetected(false)
                .build();

        mapper.insertOrUpdate(metric);

        Optional<AiModelMetric> found = mapper.findByModelAndPeriod(
                "growth-stage-clf", "GROWTH_STAGE", "DAILY", periodStart);
        assertThat(found).isPresent();
        AiModelMetric actual = found.get();
        assertThat(actual.getAccuracy()).isEqualByComparingTo("0.9210");
        assertThat(actual.getSampleCount()).isEqualTo(1500);
        assertThat(actual.isDriftDetected()).isFalse();
    }

    @Test
    @DisplayName("UNIQUE 제약 충돌 시 insertOrUpdate는 기존 행을 갱신한다 (upsert)")
    void upsertOnUniqueConflict() {
        LocalDate periodStart = LocalDate.of(2026, 5, 18);
        AiModelMetric first = AiModelMetric.builder()
                .modelName("risk-score-model")
                .predictionType("RISK_SCORE")
                .aggregatePeriod("DAILY")
                .periodStart(periodStart)
                .accuracy(new BigDecimal("0.8000"))
                .sampleCount(100)
                .driftDetected(false)
                .build();
        mapper.insertOrUpdate(first);

        AiModelMetric updated = AiModelMetric.builder()
                .modelName("risk-score-model")
                .predictionType("RISK_SCORE")
                .aggregatePeriod("DAILY")
                .periodStart(periodStart)
                .accuracy(new BigDecimal("0.6500"))
                .sampleCount(250)
                .driftDetected(true)
                .build();
        mapper.insertOrUpdate(updated);

        long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_model_metric WHERE model_name = 'risk-score-model'",
                Long.class);
        assertThat(rowCount).isEqualTo(1);

        AiModelMetric found = mapper.findByModelAndPeriod(
                "risk-score-model", "RISK_SCORE", "DAILY", periodStart).orElseThrow();
        assertThat(found.getAccuracy()).isEqualByComparingTo("0.6500");
        assertThat(found.getSampleCount()).isEqualTo(250);
        assertThat(found.isDriftDetected()).isTrue();
    }

    @Test
    @DisplayName("findDriftDetected는 drift_detected=true인 metric만 반환한다")
    void findDriftDetected() {
        mapper.insertOrUpdate(AiModelMetric.builder()
                .modelName("m1").predictionType("GROWTH_STAGE").aggregatePeriod("DAILY")
                .periodStart(LocalDate.of(2026, 5, 17)).driftDetected(false).sampleCount(10)
                .build());
        mapper.insertOrUpdate(AiModelMetric.builder()
                .modelName("m2").predictionType("RISK_SCORE").aggregatePeriod("DAILY")
                .periodStart(LocalDate.of(2026, 5, 18)).driftDetected(true).sampleCount(20)
                .build());

        List<AiModelMetric> drifted = mapper.findDriftDetected();
        assertThat(drifted).hasSize(1);
        assertThat(drifted.get(0).getModelName()).isEqualTo("m2");
        assertThat(drifted.get(0).isDriftDetected()).isTrue();
    }

    @Test
    @DisplayName("findLatest는 모델/예측유형별 최신 period_start metric을 반환한다")
    void findLatest() {
        mapper.insertOrUpdate(AiModelMetric.builder()
                .modelName("growth-stage-clf").predictionType("GROWTH_STAGE")
                .aggregatePeriod("DAILY").periodStart(LocalDate.of(2026, 5, 10))
                .accuracy(new BigDecimal("0.7000")).sampleCount(50).driftDetected(false)
                .build());
        mapper.insertOrUpdate(AiModelMetric.builder()
                .modelName("growth-stage-clf").predictionType("GROWTH_STAGE")
                .aggregatePeriod("DAILY").periodStart(LocalDate.of(2026, 5, 18))
                .accuracy(new BigDecimal("0.9100")).sampleCount(80).driftDetected(false)
                .build());

        Optional<AiModelMetric> latest = mapper.findLatest("growth-stage-clf", "GROWTH_STAGE");
        assertThat(latest).isPresent();
        assertThat(latest.get().getPeriodStart()).isEqualTo(LocalDate.of(2026, 5, 18));
        assertThat(latest.get().getAccuracy()).isEqualByComparingTo("0.9100");
    }
}
