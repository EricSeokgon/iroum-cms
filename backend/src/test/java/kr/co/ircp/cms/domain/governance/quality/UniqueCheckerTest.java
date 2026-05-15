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
 * UniqueChecker 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006 — UNIQUE 룰 (중복 비율 측정).
 * UNIQUE는 측정값이 0보다 크면 무조건 violation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UniqueChecker — 중복 비율 측정 (REQ-DATA-006)")
class UniqueCheckerTest {

    @Mock private SafeIdentifierValidator validator;
    @Mock private DataQualityTableAllowlist allowlist;
    @Mock private JdbcTemplate jdbc;

    private UniqueChecker checker;

    @BeforeEach
    void setUp() {
        checker = new UniqueChecker(validator, allowlist);
    }

    private DataQualityRule rule() {
        return DataQualityRule.builder()
                .id(1L)
                .targetTable("users")
                .targetColumn("email")
                .ruleType("UNIQUE")
                .threshold(new BigDecimal("1.0000"))
                .severity("CRITICAL")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("supportedType — UNIQUE 반환")
    void supportedType_returnsUnique() {
        assertThat(checker.supportedType()).isEqualTo("UNIQUE");
    }

    @Test
    @DisplayName("check — 중복 비율(0.05) > 0이면 violation=true (시드 데이터 의도)")
    void check_anyDuplicate_isViolation() {
        DataQualityRule r = rule();
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("0.0500"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.0500");
        assertThat(result.detail()).contains("UNIQUE duplicate_ratio=0.0500");
    }

    @Test
    @DisplayName("check — 중복 비율(0)이면 violation=false")
    void check_noDuplicate_noViolation() {
        DataQualityRule r = rule();
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("0.0000"));

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isFalse();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("check — jdbc null 반환 시 ZERO fallback + no violation")
    void check_nullMeasured_fallbackToZero() {
        DataQualityRule r = rule();
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class)))
                .thenReturn(null);

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isFalse();
        assertThat(result.measuredValue()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("check — 컬럼 검증 실패 시 ERROR 결과")
    void check_invalidColumn_returnsError() {
        DataQualityRule r = rule();
        doThrow(new IllegalArgumentException("Column not found: users.bad_col"))
                .when(validator).validateColumn(any(), any());

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.measuredValue()).isNull();
        assertThat(result.detail()).startsWith("ERROR:");
        assertThat(result.detail()).contains("Column not found");
    }

    @Test
    @DisplayName("check — 테이블 검증 실패 시 ERROR 결과")
    void check_invalidTable_returnsError() {
        DataQualityRule r = rule();
        doThrow(new IllegalArgumentException("Unsafe table identifier: bad"))
                .when(validator).validateTable(any());

        QualityCheckResult result = checker.check(r, jdbc);

        assertThat(result.violation()).isTrue();
        assertThat(result.detail()).contains("Unsafe table identifier");
    }
}
