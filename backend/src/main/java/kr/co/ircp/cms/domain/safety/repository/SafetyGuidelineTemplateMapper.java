package kr.co.ircp.cms.domain.safety.repository;

import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 가이드라인 템플릿 MyBatis 매퍼.
 * REQ-SAFETY-005
 */
@Mapper
public interface SafetyGuidelineTemplateMapper {

    List<SafetyGuidelineTemplate> findAll();

    Optional<SafetyGuidelineTemplate> findById(@Param("id") Long id);

    Optional<SafetyGuidelineTemplate> findByCode(@Param("code") String code);

    /**
     * REQ-SAFETY-003-D-1: industry_code + risk_grade에 부합하는 PUBLISHED 최신 버전.
     */
    Optional<SafetyGuidelineTemplate> findLatestPublishedFor(
            @Param("industryCode") String industryCode,
            @Param("grade") String grade
    );

    void insert(SafetyGuidelineTemplate template);

    int update(SafetyGuidelineTemplate template);

    int archiveById(@Param("id") Long id);

    /** 동일 code의 PUBLISHED를 ARCHIVED로 전환 (신규 버전 발행 시). */
    int archivePublishedByCode(@Param("code") String code);

    /**
     * REQ-PUBLIC-SAFETY-001: PUBLISHED 상태 템플릿 공개 조회 (페이징).
     * industryCode null이면 전체 반환.
     */
    List<SafetyGuidelineTemplate> findPublished(@Param("industryCode") String industryCode);

    List<SafetyGuidelineTemplate> findPublishedPaged(
            @Param("industryCode") String industryCode,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countPublished(@Param("industryCode") String industryCode);
}
