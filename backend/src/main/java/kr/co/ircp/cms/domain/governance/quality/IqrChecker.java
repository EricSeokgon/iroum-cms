package kr.co.ircp.cms.domain.governance.quality;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * IQR 품질 룰 — Q1, Q3을 percentile_cont로 산출하고
 * [Q1 - 1.5*IQR, Q3 + 1.5*IQR] 범위 밖 행 비율(0~1)을 측정.
 *
 * <p>측정값이 threshold(이상값 허용 비율) 초과 시 위반.
 */
@Component
@RequiredArgsConstructor
public class IqrChecker implements QualityChecker {

    private final SafeIdentifierValidator validator;

    @Override
    public String supportedType() {
        return "IQR";
    }

    @Override
    public QualityCheckResult check(DataQualityRule rule, JdbcTemplate jdbc) {
        try {
            validator.validateTable(rule.getTargetTable());
            validator.validateColumn(rule.getTargetTable(), rule.getTargetColumn());

            String col = rule.getTargetColumn();
            String tbl = rule.getTargetTable();

            // 빈 테이블·NULL-only 컬럼은 0으로 graceful fallback
            String sql = "WITH stats AS ("
                    + "  SELECT percentile_cont(0.25) WITHIN GROUP (ORDER BY " + col + ") AS q1, "
                    + "         percentile_cont(0.75) WITHIN GROUP (ORDER BY " + col + ") AS q3, "
                    + "         COUNT(*) FILTER (WHERE " + col + " IS NOT NULL) AS total "
                    + "  FROM " + tbl
                    + ") "
                    + "SELECT COALESCE( "
                    + "  CASE WHEN total > 0 AND q1 IS NOT NULL AND q3 IS NOT NULL THEN "
                    + "    (SELECT COUNT(*) FROM " + tbl + " "
                    + "     WHERE " + col + " < (q1 - 1.5*(q3-q1)) "
                    + "        OR " + col + " > (q3 + 1.5*(q3-q1)))::numeric / total::numeric "
                    + "  ELSE 0 END, 0) AS outlier_ratio "
                    + "FROM stats";

            BigDecimal measured = jdbc.queryForObject(sql, BigDecimal.class);
            if (measured == null) {
                measured = BigDecimal.ZERO;
            }
            measured = measured.setScale(4, RoundingMode.HALF_UP);

            boolean violation = measured.compareTo(rule.getThreshold()) > 0;
            String detail = "IQR outlier_ratio=" + measured
                    + " threshold=" + rule.getThreshold()
                    + " sql=[" + sql + "]";
            return new QualityCheckResult(measured, violation, detail);
        } catch (IllegalArgumentException e) {
            return QualityCheckResult.error(e.getMessage());
        }
    }
}
