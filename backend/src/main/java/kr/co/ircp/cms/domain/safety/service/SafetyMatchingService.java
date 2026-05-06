package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.MatchResponse;

/**
 * 사고사례 매칭 서비스 인터페이스.
 *
 * REQ-SAFETY-002-D: 키워드 가중치 기반 매칭 (industry 0.4 / process 0.3 / hazard 0.2 / equipment 0.1)
 *
 * // @MX:ANCHOR: [AUTO] SafetyMatchingService — 매칭 알고리즘 도메인 계약
 * // @MX:REASON: SafetyGuidelineService, SafetyMatchingController, SafetyReportController에서 참조 (fan_in >= 3)
 * // @MX:SPEC: REQ-SAFETY-002
 */
public interface SafetyMatchingService {

    /**
     * 본인 회사 프로필 기반 매칭 실행.
     *
     * @param companyId 인증된 회사 사용자 ID
     * @param topN      반환할 상위 N (1~20, default 5)
     * @return 매칭 결과 (캐시 포함)
     */
    MatchResponse matchForCompany(Long companyId, int topN);

    /** TTL 1시간 이내의 캐시 결과를 조회 (없으면 빈 결과). */
    MatchResponse getCachedForProfile(Long profileId, int topN);
}
