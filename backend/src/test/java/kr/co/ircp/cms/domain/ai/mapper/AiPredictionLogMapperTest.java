package kr.co.ircp.cms.domain.ai.mapper;

import kr.co.ircp.cms.domain.ai.model.AiPredictionLog;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-AI-001 Step 1 — ai_prediction_log MyBatis 매퍼 RED 테스트.
 *
 * <p>예측 로그 적재/조회 및 상태 전이(SUCCESS/ML_ERROR/TIMEOUT/FALLBACK)를 검증한다.
 */
// @MX:SPEC: SPEC-CMS-AI-001
@DisplayName("AiPredictionLogMapper IT (SPEC-CMS-AI-001)")
class AiPredictionLogMapperTest extends AbstractIntegrationTest {

    @Autowired AiPredictionLogMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM ai_prediction_log");
    }

    @Test
    @DisplayName("save 후 findById로 동일 예측 로그를 조회한다")
    void saveAndFindById() {
        AiPredictionLog log = AiPredictionLog.builder()
                .predictionType("GROWTH_STAGE")
                .modelName("growth-stage-clf")
                .modelVersion("1.0.0")
                .requestRef("req-123")
                .inputFeatures("{\"ksicCode\":\"62010\",\"capitalAmount\":50000000,\"foundingYear\":2020}")
                .outputResult("{\"stage\":\"GROWTH\"}")
                .confidence(new java.math.BigDecimal("0.8732"))
                .latencyMs(142)
                .status("SUCCESS")
                .build();

        mapper.insert(log);

        assertThat(log.getId()).isNotNull();

        Optional<AiPredictionLog> found = mapper.findById(log.getId());
        assertThat(found).isPresent();
        AiPredictionLog actual = found.get();
        assertThat(actual.getPredictionType()).isEqualTo("GROWTH_STAGE");
        assertThat(actual.getModelName()).isEqualTo("growth-stage-clf");
        assertThat(actual.getModelVersion()).isEqualTo("1.0.0");
        assertThat(actual.getInputFeatures()).contains("62010");
        assertThat(actual.getStatus()).isEqualTo("SUCCESS");
        assertThat(actual.getLatencyMs()).isEqualTo(142);
        assertThat(actual.getPredictedAt()).isNotNull();
    }

    @Test
    @DisplayName("status 전이: SUCCESS / ML_ERROR / TIMEOUT / FALLBACK 각각 저장된다")
    void statusTransitions() {
        for (String status : List.of("SUCCESS", "ML_ERROR", "TIMEOUT", "FALLBACK")) {
            AiPredictionLog log = AiPredictionLog.builder()
                    .predictionType("RISK_SCORE")
                    .modelName("risk-score-model")
                    .modelVersion("2.1.0")
                    .inputFeatures("{\"ksicCode\":\"47\"}")
                    .status(status)
                    .build();
            mapper.insert(log);

            Optional<AiPredictionLog> found = mapper.findById(log.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(status);
        }

        List<AiPredictionLog> riskLogs = mapper.findByPredictionType("RISK_SCORE", 100);
        assertThat(riskLogs).hasSize(4);
        assertThat(riskLogs).extracting(AiPredictionLog::getStatus)
                .containsExactlyInAnyOrder("SUCCESS", "ML_ERROR", "TIMEOUT", "FALLBACK");
    }

    @Test
    @DisplayName("updateStatus로 예측 결과와 상태를 갱신한다 (라벨링 후 actual_value)")
    void updateStatusWithActual() {
        AiPredictionLog log = AiPredictionLog.builder()
                .predictionType("GROWTH_STAGE")
                .modelName("growth-stage-clf")
                .modelVersion("1.0.0")
                .inputFeatures("{\"ksicCode\":\"62010\"}")
                .status("SUCCESS")
                .build();
        mapper.insert(log);

        int updated = mapper.updateStatus(log.getId(), "FALLBACK",
                "{\"actualStage\":\"MATURITY\"}");
        assertThat(updated).isEqualTo(1);

        Optional<AiPredictionLog> found = mapper.findById(log.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("FALLBACK");
        assertThat(found.get().getActualValue()).contains("MATURITY");
    }
}
