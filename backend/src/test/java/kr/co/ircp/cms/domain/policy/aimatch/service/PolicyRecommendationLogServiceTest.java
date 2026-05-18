package kr.co.ircp.cms.domain.policy.aimatch.service;

import kr.co.ircp.cms.common.util.IpHashUtil;
import kr.co.ircp.cms.domain.policy.aimatch.entity.PolicyRecommendationLogEntity;
import kr.co.ircp.cms.domain.policy.aimatch.repository.PolicyRecommendationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * PolicyRecommendationLogService 단위 테스트 (RED).
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-014 — session_ref는 SHA-256(64 hex), 평문 미저장.
 * VIEWED 추천 행은 policy_id=NULL, JSONB 직렬화 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyRecommendationLogService — 세션 해시·적재 (SPEC-CMS-AI-002)")
class PolicyRecommendationLogServiceTest {

    @Mock
    private PolicyRecommendationLogMapper mapper;

    private PolicyRecommendationLogService service;

    @BeforeEach
    void setUp() {
        service = new PolicyRecommendationLogService(mapper);
    }

    @Test
    @DisplayName("AC-PM-012: 추천 로그 적재 시 sessionRef는 64자 hex (SHA-256), 평문 미포함")
    void recommendationLogSessionHashed() {
        // service.logRecommendation 은 이미 해시된 sessionRef 를 받는 계약.
        // 호출자(PolicyMatchService/Controller)가 IpHashUtil 로 해시하므로
        // 여기서는 해시 입력 → 64 hex 저장 및 VIEWED/policy_id=NULL 을 검증.
        String hashed = IpHashUtil.sha256Hex("raw-token-123");

        service.logRecommendation(
                hashed,
                Map.of("ksic_code", "62010", "employee_count", 10),
                "AI 정책",
                List.of(101L, 88L, 203L),
                Map.of("101", Map.of("semantic", 0.8, "rule", 0.7, "hybrid", 0.74)));

        ArgumentCaptor<PolicyRecommendationLogEntity> captor =
                ArgumentCaptor.forClass(PolicyRecommendationLogEntity.class);
        verify(mapper).insertLog(captor.capture());
        PolicyRecommendationLogEntity entity = captor.getValue();

        assertThat(entity.getSessionRef())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .doesNotContain("raw-token-123");
        assertThat(entity.getInteractionType()).isEqualTo("VIEWED");
        assertThat(entity.getPolicyId()).isNull();
        assertThat(entity.getRecommendedPolicyIds()).contains("101", "88", "203");
        assertThat(entity.getCompanyProfile()).contains("ksic_code");
    }

    @Test
    @DisplayName("AC-PM-012: 피드백 로그 적재 시 policy_id 채움, interacted_at 설정")
    void feedbackLogPopulatesPolicyId() {
        String hashed = IpHashUtil.sha256Hex("session-xyz");

        service.logFeedback(hashed, "APPLIED", 999L);

        ArgumentCaptor<PolicyRecommendationLogEntity> captor =
                ArgumentCaptor.forClass(PolicyRecommendationLogEntity.class);
        verify(mapper).insertLog(captor.capture());
        PolicyRecommendationLogEntity entity = captor.getValue();

        assertThat(entity.getInteractionType()).isEqualTo("APPLIED");
        assertThat(entity.getPolicyId()).isEqualTo(999L);
        assertThat(entity.getInteractedAt()).isNotNull();
        assertThat(entity.getSessionRef()).hasSize(64).doesNotContain("session-xyz");
    }
}
