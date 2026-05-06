package kr.co.ircp.cms.domain.policy.matching.service;

import kr.co.ircp.cms.domain.policy.matching.dto.CompanyProfileUpsertRequest;
import kr.co.ircp.cms.domain.policy.matching.dto.PolicyMatchResponse;

/**
 * 정책 매칭 알고리즘 서비스.
 * REQ-POLICY-002
 */
public interface PolicyMatchingService {

    /** 기업 프로필 → TOP N 정책 매칭 (캐시 7일 TTL). */
    PolicyMatchResponse matchForCompany(Long companyId, int topN);

    /** 캐시된 결과만 조회 (만료 미경과). */
    PolicyMatchResponse getCachedResults(Long companyId, int topN);

    /** 기업 프로필 등록·수정 (UPSERT) — 매칭 캐시 자동 무효화. */
    void upsertCompanyProfile(CompanyProfileUpsertRequest request);
}
