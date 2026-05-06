package kr.co.ircp.cms.domain.safety.repository;

import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 가이드라인 보고서 MyBatis 매퍼.
 * REQ-SAFETY-003
 */
@Mapper
public interface SafetyGuidelineReportMapper {

    void insert(SafetyGuidelineReport report);

    Optional<SafetyGuidelineReport> findByUuid(@Param("uuid") UUID uuid);

    Optional<SafetyGuidelineReport> findById(@Param("id") Long id);

    List<SafetyGuidelineReport> findByCompanyProfileId(
            @Param("companyProfileId") Long companyProfileId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByCompanyProfileId(@Param("companyProfileId") Long companyProfileId);

    List<SafetyGuidelineReport> findAll(
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAll();

    int updatePdfPath(@Param("uuid") UUID uuid, @Param("pdfPath") String pdfPath);

    int incrementAccessedCount(@Param("uuid") UUID uuid);
}
