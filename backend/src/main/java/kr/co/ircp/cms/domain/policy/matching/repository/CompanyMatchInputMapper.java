package kr.co.ircp.cms.domain.policy.matching.repository;

import kr.co.ircp.cms.domain.policy.matching.entity.CompanyMatchInput;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 기업 프로필 매퍼 (매칭 입력).
 * REQ-POLICY-002
 */
@Mapper
public interface CompanyMatchInputMapper {

    Optional<CompanyMatchInput> findByCompanyId(@Param("companyId") Long companyId);

    void insert(CompanyMatchInput input);

    int update(CompanyMatchInput input);
}
