package kr.co.ircp.cms.domain.governance.quality;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * RangeChecker 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006 — RANGE 룰 (min/max 범위 밖 비율 측정).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RangeChecker — 범위 밖 비율 측정 (REQ-DATA-006)")
class RangeCheckerTest {

    @Mock private SafeIdentifierValidator validator;
    @Mock private JdbcTemplate jdbc;

    private RangeChecker checker;

    @BeforeEach
    void setUp() {
        checker = new RangeChecker(validator);
    }

    private DataQualityRule.DataQualityRuleBuilder baseRule() {
        return DataQualityRule.builder()
                .id(1L)
                .targetTable("orders")
                .targetColumn("amount")
                .ruleType("RANGE")
                .threshold(new BigDecimal("0.05"))
                .severity("WARN")
                .status("ACTIVE");
    }

    @Test
    @DisplayName("supportedType — RANGE 반환")
    void supportedType_returnsRange() {
        assertThat(checker.supportedType()).isEqualTo("RANGE");
    }

    @Test
    @DisplayName("check — min과 max 모두 NULL이면 ERROR 결과")
    void check_bothNull_returnsError() {
        DataQualityRule r = baseRule().rangeMin(null).rangeMax(null).build();

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isNull();
        assertThat(result.detail()).contains("RANGE rule requires range_min or range_max");
    }

    @Test
    @DisplayName("check — min과 max 모두 지정, 범위 밖 비율(0.10)이 threshold(0.05) 초과 → violation")
    void check_bothBounds_violation() {
        DataQualityRule r = baseRule()
                .rangeMin(new BigDecimal("0"))
                .rangeMax(new BigDecimal("1000"))
                .build();
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                .thenReturn(new BigDecimal("0.1000"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.1000");
        assertThat(result.detail()).contains("RANGE measured=0.1000");
        assertThat(result.detail()).contains("min=0");
        assertThat(result.detail()).contains("max=1000");
    }

    @Test
    @DisplayName("check — min만 지정 (max=null), violation=false")
    void check_minOnly_below_threshold() {
        DataQualityRule r = baseRule()
                .rangeMin(new BigDecimal("0"))
                .rangeMax(null)
                .build();
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                .thenReturn(new BigDecimal("0.0100"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isFalse();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.0100");
    }

    @Test
    @DisplayName("check — max만 지정 (min=null) 동작 검증")
    void check_maxOnly_works() {
        DataQualityRule r = baseRule()
                .rangeMin(null)
                .rangeMax(new BigDecimal("1000"))
                .build();
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                .thenReturn(new BigDecimal("0.0100"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isFalse();
        assertThat(result.detail()).contains("max=1000");
    }

    @Test
    @DisplayName("check — jdbc가 null 반환 시 ZERO 로 fallback")
    void check_nullMeasured_fallbackToZero() {
        DataQualityRule r = baseRule()
                .rangeMin(new BigDecimal("0"))
                .rangeMax(new BigDecimal("1000"))
                .build();
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                .thenReturn(null);

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isFalse();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("check — 식별자 검증 실패 시 ERROR 결과")
    void check_validatorThrows_returnsError() {
        DataQualityRule r = baseRule()
                .rangeMin(new BigDecimal("0"))
                .rangeMax(new BigDecimal("1000"))
                .build();
        doThrow(new IllegalArgumentException("Table not found in current schema: orders"))
                .when(validator).validateTable(any());

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.detail()).startsWith("ERROR:");
        assertThat(result.detail()).contains("Table not found");
    }
}
