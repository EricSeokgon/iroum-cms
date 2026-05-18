package kr.co.ircp.cms.domain.ai.mapper;

import kr.co.ircp.cms.domain.ai.model.AiRetrainQueue;
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
 * SPEC-CMS-AI-001 Step 1 — ai_retrain_queue MyBatis 매퍼 RED 테스트.
 *
 * <p>재학습 큐 enqueue, QUEUED 조회, 상태 전이를 검증한다.
 */
// @MX:SPEC: SPEC-CMS-AI-001
@DisplayName("AiRetrainQueueMapper IT (SPEC-CMS-AI-001)")
class AiRetrainQueueMapperTest extends AbstractIntegrationTest {

    @Autowired AiRetrainQueueMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM ai_retrain_queue");
    }

    @Test
    @DisplayName("enqueue 후 findById로 큐 항목을 조회한다 (기본 status=QUEUED)")
    void enqueueAndFindById() {
        AiRetrainQueue item = AiRetrainQueue.builder()
                .modelName("growth-stage-clf")
                .triggerReason("DRIFT_ACCURACY")
                .triggerDetail("{\"accuracyDrop\":0.15,\"threshold\":0.10}")
                .requestedBy(42L)
                .build();

        mapper.insert(item);

        assertThat(item.getId()).isNotNull();
        Optional<AiRetrainQueue> found = mapper.findById(item.getId());
        assertThat(found).isPresent();
        AiRetrainQueue actual = found.get();
        assertThat(actual.getModelName()).isEqualTo("growth-stage-clf");
        assertThat(actual.getTriggerReason()).isEqualTo("DRIFT_ACCURACY");
        assertThat(actual.getStatus()).isEqualTo("QUEUED");
        assertThat(actual.getTriggerDetail()).contains("accuracyDrop");
        assertThat(actual.getRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("findQueued는 QUEUED 상태 항목만 요청 시각 오름차순으로 반환한다")
    void findQueuedOnly() {
        AiRetrainQueue queued1 = AiRetrainQueue.builder()
                .modelName("m1").triggerReason("DRIFT_ERROR").build();
        AiRetrainQueue queued2 = AiRetrainQueue.builder()
                .modelName("m2").triggerReason("MANUAL").build();
        AiRetrainQueue done = AiRetrainQueue.builder()
                .modelName("m3").triggerReason("DRIFT_ACCURACY").build();
        mapper.insert(queued1);
        mapper.insert(queued2);
        mapper.insert(done);
        mapper.updateStatus(done.getId(), "DONE");

        List<AiRetrainQueue> queued = mapper.findQueued();
        assertThat(queued).hasSize(2);
        assertThat(queued).extracting(AiRetrainQueue::getStatus)
                .containsOnly("QUEUED");
        assertThat(queued).extracting(AiRetrainQueue::getModelName)
                .containsExactlyInAnyOrder("m1", "m2");
    }

    @Test
    @DisplayName("updateStatus로 QUEUED -> ACKNOWLEDGED -> IN_PROGRESS -> DONE 전이한다")
    void updateStatusTransitions() {
        AiRetrainQueue item = AiRetrainQueue.builder()
                .modelName("risk-score-model").triggerReason("MANUAL").build();
        mapper.insert(item);

        for (String next : List.of("ACKNOWLEDGED", "IN_PROGRESS", "DONE")) {
            assertThat(mapper.updateStatus(item.getId(), next)).isEqualTo(1);
            assertThat(mapper.findById(item.getId()).orElseThrow().getStatus())
                    .isEqualTo(next);
        }
    }
}
