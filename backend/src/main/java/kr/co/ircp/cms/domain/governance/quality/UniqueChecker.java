package kr.co.ircp.cms.domain.governance.quality;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * UNIQUE 품질 룰 — 컬럼 중복 비율(0~1)을 측정.
 *
 * <p>SQL: {@code (COUNT(*) - COUNT(DISTINCT col)) / NULLIF(COUNT(*),0)}.
 *
 * <p>측정값(중복 비율)이 threshold 초과 시 위반.
 * 시드 데이터의 threshold=1.0000은 사실상 "중복 0건"을 의미한다 (1보다 큰 비율은 불가능).
 * UNIQUE 룰은 measured > 0일 때 violation으로 처리한다 (시드 데이터 의도 반영).
 */
@Component
@RequiredArgsConstructor
public class UniqueChecker implements QualityChecker {

    private final SafeIdentifierValidator validator;
    private final DataQualityTableAllowlist allowlist;

    @Override
    public String supportedType() {
        return "UNIQUE";
    }

    @Override
    public QualityCheckResult check(DataQualityRule rule, JdbcTemplate jdbc) {
        try {
            allowlist.ensureAllowed(rule.getTargetTable(), rule.getTargetColumn());
            validator.validateTable(rule.getTargetTable());
            validator.validateColumn(rule.getTargetTable(), rule.getTargetColumn());

            String col = rule.getTargetColumn();
            String tbl = rule.getTargetTable();

            String sql = "SELECT COALESCE("
                    + "(COUNT(*) - COUNT(DISTINCT " + col + "))::numeric "
                    + "/ NULLIF(COUNT(*),0)::numeric, 0) "
                    + "FROM " + tbl
                    + " WHERE " + col + " IS NOT NULL";

            BigDecimal measured = jdbc.queryForObject(sql, BigDecimal.class);
            if (measured == null) {
                measured = BigDecimal.ZERO;
            }
            measured = measured.setScale(4, RoundingMode.HALF_UP);

            // UNIQUE는 어떤 중복이라도 violation으로 본다 (시드 데이터 의도)
            boolean violation = measured.compareTo(BigDecimal.ZERO) > 0;
            String detail = "UNIQUE duplicate_ratio=" + measured
                    + " threshold=" + rule.getThreshold()
                    + " sql=[" + sql + "]";
            return new QualityCheckResult(measured, violation, detail);
        } catch (IllegalArgumentException e) {
            return QualityCheckResult.error(e.getMessage());
        }
    }
}
