package kr.co.ircp.cms.domain.policy.aimatch.repository;

import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchMetricsRequest;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchMetricsResponse;
import kr.co.ircp.cms.domain.policy.aimatch.entity.PolicyRecommendationLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 정책 추천/피드백 로그 매퍼.
 *
 * <p>SPEC-CMS-AI-002 — 추천/피드백 행 적재 + 품질 지표 집계(CTR/전환율/커버리지).
 */
@Mapper
public interface PolicyRecommendationLogMapper {

    /** 추천/피드백 행 적재 (비동기 호출). */
    void insertLog(PolicyRecommendationLogEntity entity);

    /**
     * 기간 필터 기반 지표 집계.
     *
     * <p>CTR = DISTINCT session_ref 기준 CLICKED 보유 세션 / VIEWED 세션,
     * 신청 전환율 = APPLIED 세션 / VIEWED 세션. coverage는 서비스 레이어에서 보정한다.
     */
    PolicyMatchMetricsResponse findMetrics(PolicyMatchMetricsRequest req);

    /**
     * 기간 내 recommended_policy_ids에 1회 이상 등장한 고유 정책 수.
     *
     * <p>REQ-PM-016 — 커버리지 분자(distinct recommended policy IDs).
     */
    long countRecommendedDistinctPolicies(PolicyMatchMetricsRequest req);

    /**
     * SPEC-CMS-007 활성 정책 총수 (status='ACTIVE' AND 마감 미경과).
     *
     * <p>REQ-PM-016 — 커버리지 분모.
     */
    long countActivePolicies();

    /**
     * SPEC-CMS-007 활성 정책 ID 목록 (마감 임박 순, 상한 제한).
     *
     * <p>비회원/프로필 미존재 시 ML 시맨틱 후보 풀로 사용한다(규칙 점수 미산출 케이스).
     */
    java.util.List<Long> findActivePolicyIds(int limit);
}
