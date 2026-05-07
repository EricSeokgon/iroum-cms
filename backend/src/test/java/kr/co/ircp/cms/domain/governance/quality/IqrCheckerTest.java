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
 * IqrChecker 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006 — IQR 룰 (Q1, Q3을 percentile_cont로 산출,
 * [Q1 - 1.5*IQR, Q3 + 1.5*IQR] 범위 밖 비율 측정).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IqrChecker — 이상값 비율 측정 (REQ-DATA-006)")
class IqrCheckerTest {

    @Mock private SafeIdentifierValidator validator;
    @Mock private JdbcTemplate jdbc;

    private IqrChecker checker;

    @BeforeEach
    void setUp() {
        checker = new IqrChecker(validator);
    }

    private DataQualityRule rule(BigDecimal threshold) {
        return DataQualityRule.builder()
                .id(1L)
                .targetTable("orders")
                .targetColumn("amount")
                .ruleType("IQR")
                .threshold(threshold)
                .severity("WARN")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("supportedType — IQR 반환")
    void supportedType_returnsIqr() {
        assertThat(checker.supportedType()).isEqualTo("IQR");
    }

    @Test
    @DisplayName("check — outlier_ratio(0.10) > threshold(0.05)이면 violation=true")
    void check_aboveThreshold_violation() {
        DataQualityRule r = rule(new BigDecimal("0.05"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("0.1000"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.1000");
        assertThat(result.detail()).contains("IQR outlier_ratio=0.1000");
    }

    @Test
    @DisplayName("check — outlier_ratio(0.01) <= threshold(0.05)이면 violation=false")
    void check_belowThreshold_noViolation() {
        DataQualityRule r = rule(new BigDecimal("0.05"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("0.0100"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isFalse();
    }

    @Test
    @DisplayName("check — jdbc가 null 반환 시 ZERO 로 fallback")
    void check_nullMeasured_fallbackToZero() {
        DataQualityRule r = rule(new BigDecimal("0.05"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(null);

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isFalse();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("check — 식별자 검증 실패 시 ERROR 결과")
    void check_validatorThrows_returnsError() {
        DataQualityRule r = rule(new BigDecimal("0.05"));
        doThrow(new IllegalArgumentException("Unsafe column identifier: bad'col"))
                .when(validator).validateColumn(any(), any());

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isNull();
        assertThat(result.detail()).startsWith("ERROR:");
    }
}
