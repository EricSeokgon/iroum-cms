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
 * FreshnessChecker 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006 — FRESHNESS 룰 (마지막 created_at 이후 경과 시간 측정).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FreshnessChecker — 데이터 신선도 측정 (REQ-DATA-006)")
class FreshnessCheckerTest {

    @Mock private SafeIdentifierValidator validator;
    @Mock private DataQualityTableAllowlist allowlist;
    @Mock private JdbcTemplate jdbc;

    private FreshnessChecker checker;

    @BeforeEach
    void setUp() {
        checker = new FreshnessChecker(validator, allowlist);
    }

    private DataQualityRule rule(BigDecimal threshold) {
        return DataQualityRule.builder()
                .id(1L)
                .targetTable("data_dictionary")
                .targetColumn(null) // FRESHNESS는 컬럼 무관, created_at 고정
                .ruleType("FRESHNESS")
                .threshold(threshold)
                .severity("WARN")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("supportedType — FRESHNESS 반환")
    void supportedType_returnsFreshness() {
        assertThat(checker.supportedType()).isEqualTo("FRESHNESS");
    }

    @Test
    @DisplayName("check — created_at 컬럼이 없으면 9999 fallback + violation=true")
    void check_noCreatedAtColumn_returns9999Fallback() {
        DataQualityRule r = rule(new BigDecimal("24"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isEqualByComparingTo("9999");
        assertThat(result.detail()).contains("no created_at column");
        assertThat(result.detail()).contains("data_dictionary");
    }

    @Test
    @DisplayName("check — created_at 존재 + 경과 시간(48h) > threshold(24h) → violation=true")
    void check_staleData_violation() {
        DataQualityRule r = rule(new BigDecimal("24"));
        // 1단계: 컬럼 존재 확인
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        // 2단계: 시간 측정 — 48시간 경과
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("48.0000"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isEqualByComparingTo("48.0000");
        assertThat(result.detail()).contains("FRESHNESS hours_since_last=48.0000");
    }

    @Test
    @DisplayName("check — 경과 시간(12h) <= threshold(24h) → violation=false")
    void check_freshData_noViolation() {
        DataQualityRule r = rule(new BigDecimal("24"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("12.0000"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isFalse();
        assertThat(result.measuredValue()).isEqualByComparingTo("12.0000");
    }

    @Test
    @DisplayName("check — 컬럼 존재하지만 시간 측정이 null → 9999 fallback + violation 판정")
    void check_nullMeasured_fallbackTo9999() {
        DataQualityRule r = rule(new BigDecimal("24"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(null);

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isEqualByComparingTo("9999");
    }

    @Test
    @DisplayName("check — hasCreatedAt 자체가 null이면 9999 fallback")
    void check_nullHasCreatedAt_returns9999() {
        DataQualityRule r = rule(new BigDecimal("24"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(null);

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isEqualByComparingTo("9999");
    }

    @Test
    @DisplayName("check — 식별자 검증 실패 시 ERROR 결과")
    void check_validatorThrows_returnsError() {
        DataQualityRule r = rule(new BigDecimal("24"));
        doThrow(new IllegalArgumentException("Unsafe table identifier: bad'name"))
                .when(validator).validateTable(any());

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.detail()).startsWith("ERROR:");
        assertThat(result.detail()).contains("Unsafe table identifier");
    }
}
