package kr.co.ircp.cms.domain.governance.quality;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * QualityCheckerRegistry 단위 테스트.
 *
 * <p>룰 타입 → checker dispatch 인덱스 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QualityCheckerRegistry — 타입별 checker dispatch")
class QualityCheckerRegistryTest {

    @Mock private QualityChecker nullRatioChecker;
    @Mock private QualityChecker rangeChecker;
    @Mock private JdbcTemplate jdbc;

    @Test
    @DisplayName("forType — 등록된 NULL_RATIO 타입 조회 성공")
    void forType_registered_returnsChecker() {
        when(nullRatioChecker.supportedType()).thenReturn("NULL_RATIO");
        when(rangeChecker.supportedType()).thenReturn("RANGE");

        QualityCheckerRegistry registry =
                new QualityCheckerRegistry(List.of(nullRatioChecker, rangeChecker));

        assertThat(registry.forType("NULL_RATIO")).isSameAs(nullRatioChecker);
        assertThat(registry.forType("RANGE")).isSameAs(rangeChecker);
    }

    @Test
    @DisplayName("forType — 미등록 타입은 IllegalArgumentException")
    void forType_unregistered_throws() {
        when(nullRatioChecker.supportedType()).thenReturn("NULL_RATIO");

        QualityCheckerRegistry registry = new QualityCheckerRegistry(List.of(nullRatioChecker));

        assertThatThrownBy(() -> registry.forType("UNKNOWN_TYPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported quality rule type")
                .hasMessageContaining("UNKNOWN_TYPE");
    }

    @Test
    @DisplayName("supports — 등록된 타입은 true, 미등록은 false")
    void supports_registeredAndUnregistered() {
        when(nullRatioChecker.supportedType()).thenReturn("NULL_RATIO");

        QualityCheckerRegistry registry = new QualityCheckerRegistry(List.of(nullRatioChecker));

        assertThat(registry.supports("NULL_RATIO")).isTrue();
        assertThat(registry.supports("UNKNOWN")).isFalse();
    }

    @Test
    @DisplayName("constructor — 빈 List도 안전하게 동작 (모든 supports false)")
    void constructor_emptyList_works() {
        QualityCheckerRegistry registry = new QualityCheckerRegistry(List.of());

        assertThat(registry.supports("NULL_RATIO")).isFalse();
        assertThatThrownBy(() -> registry.forType("NULL_RATIO"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("constructor — 동일한 supportedType이 여러 개면 마지막 빈이 우선")
    void constructor_duplicateType_lastOneWins() {
        QualityChecker first = org.mockito.Mockito.mock(QualityChecker.class);
        QualityChecker last = org.mockito.Mockito.mock(QualityChecker.class);
        lenient().when(first.supportedType()).thenReturn("NULL_RATIO");
        lenient().when(last.supportedType()).thenReturn("NULL_RATIO");

        QualityCheckerRegistry registry = new QualityCheckerRegistry(List.of(first, last));

        assertThat(registry.forType("NULL_RATIO")).isSameAs(last);
    }

    @Test
    @DisplayName("forType — 반환된 checker로 실제 dispatch 가능")
    void forType_dispatch_invokesChecker() {
        when(nullRatioChecker.supportedType()).thenReturn("NULL_RATIO");
        DataQualityRule rule = DataQualityRule.builder()
                .id(1L).targetTable("users").targetColumn("email").ruleType("NULL_RATIO")
                .threshold(new BigDecimal("0.05")).severity("WARN").status("ACTIVE").build();
        QualityCheckResult expected = new QualityCheckResult(new BigDecimal("0.10"), true, "ok");
        when(nullRatioChecker.check(rule, jdbc)).thenReturn(expected);

        QualityCheckerRegistry registry = new QualityCheckerRegistry(List.of(nullRatioChecker));
        QualityChecker resolved = registry.forType("NULL_RATIO");
        QualityCheckResult result = resolved.check(rule, jdbc);

        assertThat(result).isSameAs(expected);
    }
}
