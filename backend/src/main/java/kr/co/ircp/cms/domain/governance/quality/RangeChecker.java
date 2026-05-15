package kr.co.ircp.cms.domain.governance.quality;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * RANGE 품질 룰 — 컬럼이 [range_min, range_max] 범위 밖에 있는 행 비율(0~1)을 측정.
 *
 * <p>SQL: {@code COUNT(*) FILTER (WHERE col < range_min OR col > range_max) / NULLIF(COUNT(*),0)}.
 *
 * <p>range_min/max NULL이면 해당 방향 검사를 생략한다.
 */
@Component
@RequiredArgsConstructor
public class RangeChecker implements QualityChecker {

    private final SafeIdentifierValidator validator;
    private final DataQualityTableAllowlist allowlist;

    @Override
    public String supportedType() {
        return "RANGE";
    }

    @Override
    public QualityCheckResult check(DataQualityRule rule, JdbcTemplate jdbc) {
        try {
            allowlist.ensureAllowed(rule.getTargetTable(), rule.getTargetColumn());
            validator.validateTable(rule.getTargetTable());
            validator.validateColumn(rule.getTargetTable(), rule.getTargetColumn());

            BigDecimal min = rule.getRangeMin();
            BigDecimal max = rule.getRangeMax();
            if (min == null && max == null) {
                return QualityCheckResult.error("RANGE rule requires range_min or range_max");
            }

            // 범위 밖 카운트를 위한 동적 조건 — 값은 ?로 바인딩
            StringBuilder cond = new StringBuilder();
            java.util.List<Object> params = new java.util.ArrayList<>();
            if (min != null) {
                cond.append(rule.getTargetColumn()).append(" < ?");
                params.add(min);
            }
            if (max != null) {
                if (cond.length() > 0) cond.append(" OR ");
                cond.append(rule.getTargetColumn()).append(" > ?");
                params.add(max);
            }
            String sql = "SELECT COALESCE("
                    + "COUNT(*) FILTER (WHERE " + cond + ")::numeric "
                    + "/ NULLIF(COUNT(*),0)::numeric, 0) "
                    + "FROM " + rule.getTargetTable();

            BigDecimal measured = jdbc.queryForObject(sql, BigDecimal.class, params.toArray());
            if (measured == null) {
                measured = BigDecimal.ZERO;
            }
            measured = measured.setScale(4, RoundingMode.HALF_UP);

            boolean violation = measured.compareTo(rule.getThreshold()) > 0;
            String detail = "RANGE measured=" + measured
                    + " threshold=" + rule.getThreshold()
                    + " min=" + min + " max=" + max
                    + " sql=[" + sql + "]";
            return new QualityCheckResult(measured, violation, detail);
        } catch (IllegalArgumentException e) {
            return QualityCheckResult.error(e.getMessage());
        }
    }
}
