package kr.co.ircp.cms.domain.governance.quality;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * NULL_RATIO 품질 룰 — 컬럼 NULL 비율(0~1)을 측정하고 threshold 초과 시 위반.
 *
 * <p>SQL: {@code COUNT(*) FILTER (WHERE col IS NULL) / NULLIF(COUNT(*),0)}.
 */
@Component
@RequiredArgsConstructor
public class NullRatioChecker implements QualityChecker {

    private final SafeIdentifierValidator validator;

    @Override
    public String supportedType() {
        return "NULL_RATIO";
    }

    @Override
    public QualityCheckResult check(DataQualityRule rule, JdbcTemplate jdbc) {
        try {
            validator.validateTable(rule.getTargetTable());
            validator.validateColumn(rule.getTargetTable(), rule.getTargetColumn());

            String sql = "SELECT COALESCE("
                    + "COUNT(*) FILTER (WHERE " + rule.getTargetColumn() + " IS NULL)::numeric "
                    + "/ NULLIF(COUNT(*),0)::numeric, 0) "
                    + "FROM " + rule.getTargetTable();
            BigDecimal measured = jdbc.queryForObject(sql, BigDecimal.class);
            if (measured == null) {
                measured = BigDecimal.ZERO;
            }
            measured = measured.setScale(4, RoundingMode.HALF_UP);

            boolean violation = measured.compareTo(rule.getThreshold()) > 0;
            String detail = "NULL_RATIO measured=" + measured
                    + " threshold=" + rule.getThreshold()
                    + " sql=[" + sql + "]";
            return new QualityCheckResult(measured, violation, detail);
        } catch (IllegalArgumentException e) {
            return QualityCheckResult.error(e.getMessage());
        }
    }
}
