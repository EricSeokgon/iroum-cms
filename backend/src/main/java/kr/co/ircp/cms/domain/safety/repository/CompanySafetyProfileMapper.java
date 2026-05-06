package kr.co.ircp.cms.domain.safety.repository;

import kr.co.ircp.cms.domain.safety.entity.CompanySafetyProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 기업 안전 프로필 MyBatis 매퍼.
 * REQ-SAFETY-002-D-1
 */
@Mapper
public interface CompanySafetyProfileMapper {

    Optional<CompanySafetyProfile> findById(@Param("id") Long id);

    Optional<CompanySafetyProfile> findByCompanyId(@Param("companyId") Long companyId);

    void insert(CompanySafetyProfile profile);

    int update(CompanySafetyProfile profile);
}
