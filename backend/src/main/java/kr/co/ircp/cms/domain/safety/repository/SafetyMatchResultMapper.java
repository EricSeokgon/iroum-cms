package kr.co.ircp.cms.domain.safety.repository;

import kr.co.ircp.cms.domain.safety.entity.SafetyMatchResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 매칭 결과 MyBatis 매퍼 (TTL 캐시).
 * REQ-SAFETY-002-D-5
 */
@Mapper
public interface SafetyMatchResultMapper {

    void insert(SafetyMatchResult result);

    /** 만료되지 않은 캐시 매칭 결과 (similarity_score DESC). */
    List<SafetyMatchResult> findActiveCacheByProfileId(
            @Param("companyProfileId") Long companyProfileId,
            @Param("limit") int limit
    );

    /** 프로필 캐시 전체 무효화 (프로필 변경 시). */
    int deleteByProfileId(@Param("companyProfileId") Long companyProfileId);

    /** 만료된 캐시 정리 (스케줄러 용도). */
    int deleteExpired();
}
