package kr.co.ircp.cms.domain.governance.repository;

import kr.co.ircp.cms.domain.governance.entity.DataQualityReport;
import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 데이터 품질 매퍼.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006~008.
 */
@Mapper
public interface DataQualityMapper {

    Optional<DataQualityRule> findRuleById(@Param("id") Long id);

    List<DataQualityRule> findActiveRules();

    /** 필터: targetTable, ruleType, status. 페이징 없음 (룰 수 적음). */
    List<DataQualityRule> findRulesFiltered(@Param("p") Map<String, Object> params);

    void insertRule(DataQualityRule rule);

    int updateRule(DataQualityRule rule);

    int deleteRule(@Param("id") Long id);

    int countReportsByRuleId(@Param("ruleId") Long ruleId);

    void insertReport(DataQualityReport report);

    int updateReportNotified(@Param("id") Long id);

    List<DataQualityReport> findReportsByRule(@Param("ruleId") Long ruleId);

    /** 필터 + 페이징: ruleId, violation, severity(via JOIN), offset, size */
    List<DataQualityReport> findReportsFiltered(@Param("p") Map<String, Object> params);

    int countReportsFiltered(@Param("p") Map<String, Object> params);

    /** NULL_RATIO 측정용 — 컬럼 NULL 비율 (0.0 ~ 1.0). */
    BigDecimal measureNullRatio(@Param("targetTable") String targetTable,
                                 @Param("targetColumn") String targetColumn);

    /** UNIQUE 측정용 — 중복 그룹 수. */
    Integer measureDuplicateGroups(@Param("targetTable") String targetTable,
                                     @Param("targetColumn") String targetColumn);

    /** FRESHNESS 측정용 — 마지막 created_at/updated_at 이후 경과 시간(시). NULL 시 9999. */
    BigDecimal measureFreshnessHours(@Param("targetTable") String targetTable);
}
