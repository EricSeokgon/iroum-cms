package kr.co.ircp.cms.domain.policy.aimatch.service;

import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyFeedbackRequest;
import kr.co.ircp.cms.domain.policy.aimatch.exception.AiFeedbackInvalidException;
import kr.co.ircp.cms.domain.policy.aimatch.repository.PolicyRecommendationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * PolicyFeedbackService 단위 테스트 (RED).
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-012/013 — CLICKED 적재, VIEWED·null policy_id 거부.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyFeedbackService — 피드백 무결성 (SPEC-CMS-AI-002)")
class PolicyFeedbackServiceTest {

    @Mock
    private PolicyRecommendationLogMapper mapper;

    private PolicyRecommendationLogService logService;
    private PolicyFeedbackService service;

    @BeforeEach
    void setUp() {
        logService = new PolicyRecommendationLogService(mapper);
        service = new PolicyFeedbackService(logService);
    }

    @Test
    @DisplayName("AC-PM-010: CLICKED + policy_id → 피드백 행 적재 (policy_id 채움)")
    void clickedFeedbackRecorded() {
        service.recordFeedback(new PolicyFeedbackRequest("raw-token-123", "CLICKED", 101L));

        ArgumentCaptor<kr.co.ircp.cms.domain.policy.aimatch.entity.PolicyRecommendationLogEntity>
                captor = ArgumentCaptor.forClass(
                kr.co.ircp.cms.domain.policy.aimatch.entity.PolicyRecommendationLogEntity.class);
        verify(mapper, times(1)).insertLog(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.getInteractionType()).isEqualTo("CLICKED");
        assertThat(entity.getPolicyId()).isEqualTo(101L);
        assertThat(entity.getInteractedAt()).isNotNull();
        // 평문 토큰이 저장되지 않고 SHA-256(64 hex)로 해시됨
        assertThat(entity.getSessionRef()).hasSize(64).doesNotContain("raw-token-123");
    }

    @Test
    @DisplayName("AC-PM-011: interaction_type=VIEWED → AI_FEEDBACK_INVALID, 무적재")
    void viewedFeedbackRejected() {
        assertThatThrownBy(() ->
                service.recordFeedback(new PolicyFeedbackRequest("tok", "VIEWED", 101L)))
                .isInstanceOf(AiFeedbackInvalidException.class);
        verify(mapper, never()).insertLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("AC-PM-011: policy_id 누락 → AI_FEEDBACK_INVALID, 무적재")
    void missingPolicyIdRejected() {
        assertThatThrownBy(() ->
                service.recordFeedback(new PolicyFeedbackRequest("tok", "CLICKED", null)))
                .isInstanceOf(AiFeedbackInvalidException.class)
                .hasMessageContaining("policy_id");
        verify(mapper, never()).insertLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("AC-PM-011: 알 수 없는 interaction_type → AI_FEEDBACK_INVALID")
    void unknownTypeRejected() {
        assertThatThrownBy(() ->
                service.recordFeedback(new PolicyFeedbackRequest("tok", "HOVERED", 1L)))
                .isInstanceOf(AiFeedbackInvalidException.class);
        verify(mapper, never()).insertLog(org.mockito.ArgumentMatchers.any());
    }
}
