package kr.co.ircp.cms.domain.governance.quality;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * FRESHNESS 품질 룰 — 마지막 created_at 이후 경과 시간(시간)을 측정.
 *
 * <p>SQL: {@code EXTRACT(EPOCH FROM (NOW() - MAX(created_at))) / 3600}.
 *
 * <p>빈 테이블이거나 created_at 컬럼이 없으면 9999로 fallback (위반).
 */
@Component
@RequiredArgsConstructor
public class FreshnessChecker implements QualityChecker {

    private final SafeIdentifierValidator validator;

    @Override
    public String supportedType() {
        return "FRESHNESS";
    }

    @Override
    public QualityCheckResult check(DataQualityRule rule, JdbcTemplate jdbc) {
        try {
            validator.validateTable(rule.getTargetTable());

            // created_at 컬럼이 없는 테이블은 9999로 처리 (위반)
            Integer hasCreatedAt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = current_schema() "
                            + "AND table_name = ? AND column_name = 'created_at'",
                    Integer.class, rule.getTargetTable());
            if (hasCreatedAt == null || hasCreatedAt == 0) {
                BigDecimal fallback = BigDecimal.valueOf(9999);
                return new QualityCheckResult(fallback, true,
                        "FRESHNESS no created_at column on " + rule.getTargetTable());
            }

            String sql = "SELECT COALESCE("
                    + "EXTRACT(EPOCH FROM (NOW() - MAX(created_at))) / 3600.0, "
                    + "9999.0)::numeric "
                    + "FROM " + rule.getTargetTable();

            BigDecimal measured = jdbc.queryForObject(sql, BigDecimal.class);
            if (measured == null) {
                measured = BigDecimal.valueOf(9999);
            }
            measured = measured.setScale(4, RoundingMode.HALF_UP);

            boolean violation = measured.compareTo(rule.getThreshold()) > 0;
            String detail = "FRESHNESS hours_since_last=" + measured
                    + " threshold=" + rule.getThreshold()
                    + " sql=[" + sql + "]";
            return new QualityCheckResult(measured, violation, detail);
        } catch (IllegalArgumentException e) {
            return QualityCheckResult.error(e.getMessage());
        }
    }
}
