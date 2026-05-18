package kr.co.ircp.cms.domain.policy.aimatch.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AI 정책 추천/피드백 로그 엔티티 (ai_policy_recommendation_log).
 *
 * <p>SPEC-CMS-AI-002 — 추천 이벤트 행(interaction_type=VIEWED, policy_id=NULL)과
 * 피드백 이벤트 행(interaction_type∈{CLICKED,APPLIED,DISMISSED}, policy_id 채움)을
 * 동일 테이블에 적재한다. JSONB 컬럼은 String raw text로 다루고 XML에서 ::jsonb 캐스팅한다
 * (AI-001 패턴). {@code sessionRef}는 SHA-256 해시(평문 미저장).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRecommendationLogEntity {

    private Long id;
    private String sessionRef;
    /** JSONB raw text — 입력 프로필 (PII 제외). */
    private String companyProfile;
    private String queryText;
    /** JSONB raw text — 순서 보존 추천 정책 ID 배열. */
    private String recommendedPolicyIds;
    /** JSONB raw text — 정책별 시맨틱/하이브리드 점수 맵. */
    private String mlScores;
    private String interactionType;
    private Long policyId;
    private Instant recommendedAt;
    private Instant interactedAt;
}
