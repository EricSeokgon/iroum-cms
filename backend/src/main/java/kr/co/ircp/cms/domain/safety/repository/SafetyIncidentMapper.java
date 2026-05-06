package kr.co.ircp.cms.domain.safety.repository;

import kr.co.ircp.cms.domain.safety.entity.SafetyIncident;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 사고사례 MyBatis 매퍼.
 * REQ-SAFETY-001
 */
@Mapper
public interface SafetyIncidentMapper {

    /** 필터 조건 페이징 조회. */
    List<SafetyIncident> findFiltered(
            @Param("industryCode") String industryCode,
            @Param("incidentType") String incidentType,
            @Param("severity") String severity,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countFiltered(
            @Param("industryCode") String industryCode,
            @Param("incidentType") String incidentType,
            @Param("severity") String severity
    );

    Optional<SafetyIncident> findById(@Param("id") Long id);

    void insert(SafetyIncident incident);

    int update(SafetyIncident incident);

    /** 논리 삭제 (status=ARCHIVED). */
    int archiveById(@Param("id") Long id);

    /** 매칭 후보: 키워드 ID 목록과 매핑된 사고사례 (industry_code 동일 우선). */
    List<SafetyIncident> findCandidatesForMatching(
            @Param("keywordIds") List<Long> keywordIds,
            @Param("industryCode") String industryCode
    );
}
