package kr.co.ircp.cms.domain.governance.repository;

import kr.co.ircp.cms.domain.governance.entity.DataQualityReport;
import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    // 보안 수정 (HIGH-5, SPEC-CMS-SECURITY-HIGH-5):
    // 이전 버전의 measureNullRatio / measureDuplicateGroups / measureFreshnessHours
    // 세 메서드는 MyBatis ${} 보간을 사용해 SQL 인젝션 위험이 있었음.
    // 실제 품질 측정은 governance/quality 패키지의 *Checker 구현체가
    // SafeIdentifierValidator + JdbcTemplate 조합으로 안전하게 수행하므로
    // 매퍼 측 measure* API 자체를 제거함 (XML / Java 동시 제거, BREAKING 사용처 없음).
}
