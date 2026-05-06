package kr.co.ircp.cms.domain.policy.matching.repository;

import kr.co.ircp.cms.domain.policy.matching.entity.PolicyMatchScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 매칭 결과 매퍼 (TTL 캐시 7일).
 * REQ-POLICY-002-D-2
 */
@Mapper
public interface PolicyMatchScoreMapper {

    void insert(PolicyMatchScore score);

    /** 만료되지 않은 매칭 결과 (score DESC). */
    List<PolicyMatchScore> findActiveCacheByCompanyId(
            @Param("companyId") Long companyId,
            @Param("limit") int limit
    );

    /** 프로필 변경 등으로 캐시 전면 무효화. */
    int deleteByCompanyId(@Param("companyId") Long companyId);

    int deleteExpired();
}
