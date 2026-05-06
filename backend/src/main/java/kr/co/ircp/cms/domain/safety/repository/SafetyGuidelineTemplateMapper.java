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
}
