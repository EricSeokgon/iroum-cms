package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.ProfileResponse;
import kr.co.ircp.cms.domain.safety.dto.ProfileUpsertRequest;

/**
 * 기업 안전 프로필 서비스.
 * REQ-SAFETY-002-D-1
 */
public interface CompanySafetyProfileService {

    /** 본인 회사 프로필 upsert (insert or update). 변경 시 매칭 캐시 무효화. */
    ProfileResponse upsertProfile(Long companyId, ProfileUpsertRequest request);

    /** 본인 회사 프로필 조회. */
    ProfileResponse getMyProfile(Long companyId);
}
