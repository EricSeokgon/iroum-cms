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
 * NullRatioChecker 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006~007 — NULL_RATIO 룰 측정 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NullRatioChecker — NULL 비율 측정 (REQ-DATA-006)")
class NullRatioCheckerTest {

    @Mock private SafeIdentifierValidator validator;
    @Mock private DataQualityTableAllowlist allowlist;
    @Mock private JdbcTemplate jdbc;

    private NullRatioChecker checker;

    @BeforeEach
    void setUp() {
        checker = new NullRatioChecker(validator, allowlist);
    }

    private DataQualityRule rule(BigDecimal threshold) {
        return DataQualityRule.builder()
                .id(1L)
                .targetTable("users")
                .targetColumn("email")
                .ruleType("NULL_RATIO")
                .threshold(threshold)
                .severity("WARN")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("supportedType — NULL_RATIO 반환")
    void supportedType_returnsNullRatio() {
        assertThat(checker.supportedType()).isEqualTo("NULL_RATIO");
    }

    @Test
    @DisplayName("check — measured(0.10) > threshold(0.05)이면 violation=true")
    void check_violation_returnsViolationTrue() {
        DataQualityRule r = rule(new BigDecimal("0.05"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("0.1000"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.1000");
        assertThat(result.detail()).contains("NULL_RATIO measured=0.1000");
        assertThat(result.detail()).contains("threshold=0.05");
    }

    @Test
    @DisplayName("check — measured(0.01) <= threshold(0.05)이면 violation=false")
    void check_belowThreshold_returnsViolationFalse() {
        DataQualityRule r = rule(new BigDecimal("0.05"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("0.0100"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isFalse();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.0100");
    }

    @Test
    @DisplayName("check — jdbc가 null 반환 시 ZERO로 fallback")
    void check_nullMeasured_fallbackToZero() {
        DataQualityRule r = rule(new BigDecimal("0.05"));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class))).thenReturn(null);

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isFalse();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("check — 식별자 검증 실패 시 ERROR 결과 반환")
    void check_invalidIdentifier_returnsError() {
        DataQualityRule r = rule(new BigDecimal("0.05"));
        doThrow(new IllegalArgumentException("Unsafe table identifier: bad'table"))
                .when(validator).validateTable(any());

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isNull();
        assertThat(result.detail()).startsWith("ERROR:");
        assertThat(result.detail()).contains("Unsafe table identifier");
    }

    @Test
    @DisplayName("check — 컬럼 검증 실패 시 ERROR 결과 반환")
    void check_invalidColumn_returnsError() {
        DataQualityRule r = rule(new BigDecimal("0.05"));
        doThrow(new IllegalArgumentException("Column not found: users.bad_col"))
                .when(validator).validateColumn(any(), any());

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.detail()).contains("Column not found");
    }
}
