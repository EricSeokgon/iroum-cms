package kr.co.ircp.cms.domain.policy.tracking.repository;

import kr.co.ircp.cms.domain.policy.tracking.entity.PolicyApplicationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 정책 신청·클릭 추적 매퍼.
 * REQ-POLICY-005
 */
@Mapper
public interface PolicyApplicationLogMapper {

    void insert(PolicyApplicationLog log);

    long countByPolicyAndAction(
            @Param("policyId") Long policyId,
            @Param("action") String action
    );
}
