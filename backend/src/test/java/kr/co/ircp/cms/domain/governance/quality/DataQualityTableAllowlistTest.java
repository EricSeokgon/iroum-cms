package kr.co.ircp.cms.domain.governance.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DataQualityTableAllowlist} 단위 테스트 — SPEC-CMS-SECURITY-HIGH-5.
 *
 * <p>커버리지 갭 P1: 35.3% → 100% (LINE)
 * <p>대상 ANCHOR: 데이터 품질 측정 SQL 인젝션 2차 방어선
 */
@DisplayName("DataQualityTableAllowlist — 데이터 품질 허용목록 게이트")
class DataQualityTableAllowlistTest {

    @Nested
    @DisplayName("allowlist 비활성(빈 목록) 시 모든 테이블 허용 — 레거시 호환")
    class WhenAllowlistEmpty {

        private final DataQualityTableAllowlist allowlist =
                new DataQualityTableAllowlist(List.of(), List.of());

        @Test
        @DisplayName("임의 테이블/컬럼은 통과한다")
        void shouldPassAnyTableWhenAllowlistEmpty() {
            // 허용목록이 비어 있으면 게이트 자체가 비활성이므로 어떤 입력도 예외가 발생하지 않는다.
            allowlist.ensureAllowed("arbitrary_table", "any_column");
            allowlist.ensureAllowed("users", null);
        }

        @Test
        @DisplayName("allowedTables/deniedColumns 스냅샷은 빈 Set을 반환한다")
        void shouldReturnEmptySnapshots() {
            assertThat(allowlist.allowedTables()).isEmpty();
            assertThat(allowlist.deniedColumns()).isEmpty();
        }
    }

    @Nested
    @DisplayName("allowlist 활성 시 — 허용목록 검증")
    class WhenAllowlistConfigured {

        private final DataQualityTableAllowlist allowlist =
                new DataQualityTableAllowlist(
                        List.of("contents", "boards"),
                        List.of("password_hash"));

        @Test
        @DisplayName("허용 테이블은 통과")
        void shouldPassWhenTableInAllowlist() {
            allowlist.ensureAllowed("contents", null);
            allowlist.ensureAllowed("boards", "title");
        }

        @Test
        @DisplayName("미등록 테이블은 IllegalArgumentException")
        void shouldRejectWhenTableNotInAllowlist() {
            assertThatThrownBy(() -> allowlist.ensureAllowed("users", "email"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("allowlist")
                    .hasMessageContaining("users");
        }

        @Test
        @DisplayName("차단 컬럼은 어떤 테이블에서도 차단된다")
        void shouldRejectGloballyDeniedColumn() {
            assertThatThrownBy(() -> allowlist.ensureAllowed("contents", "password_hash"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("globally denied")
                    .hasMessageContaining("password_hash");
        }

        @Test
        @DisplayName("스냅샷은 등록한 값을 그대로 반환")
        void shouldReturnConfiguredSnapshots() {
            Set<String> tables = allowlist.allowedTables();
            Set<String> denied = allowlist.deniedColumns();
            assertThat(tables).containsExactlyInAnyOrder("contents", "boards");
            assertThat(denied).containsExactly("password_hash");
        }

        @Test
        @DisplayName("스냅샷은 unmodifiable")
        void shouldReturnUnmodifiableSnapshots() {
            assertThatThrownBy(() -> allowlist.allowedTables().add("hacker_table"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> allowlist.deniedColumns().add("hacker_col"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("입력 검증 — null/blank 처리")
    class InputValidation {

        private final DataQualityTableAllowlist allowlist =
                new DataQualityTableAllowlist(List.of("contents"), List.of("password_hash"));

        @Test
        @DisplayName("테이블 null은 IllegalArgumentException")
        void shouldRejectNullTable() {
            assertThatThrownBy(() -> allowlist.ensureAllowed(null, "title"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target table is required");
        }

        @Test
        @DisplayName("테이블 blank는 IllegalArgumentException")
        void shouldRejectBlankTable() {
            assertThatThrownBy(() -> allowlist.ensureAllowed("   ", "title"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target table is required");
        }

        @Test
        @DisplayName("컬럼 null은 허용 (FRESHNESS 룰 호환)")
        void shouldAllowNullColumn() {
            allowlist.ensureAllowed("contents", null);
        }

        @Test
        @DisplayName("컬럼 blank는 차단 컬럼 체크에서 제외")
        void shouldSkipBlankColumnInDenyCheck() {
            allowlist.ensureAllowed("contents", "");
            allowlist.ensureAllowed("contents", "   ");
        }
    }

    @Nested
    @DisplayName("null 생성자 인자 방어")
    class NullConstructorArgs {

        @Test
        @DisplayName("생성자 인자가 모두 null이면 빈 Set으로 초기화 (NPE 없이 동작)")
        void shouldHandleNullConstructorArgs() {
            DataQualityTableAllowlist allowlist =
                    new DataQualityTableAllowlist(null, null);

            assertThat(allowlist.allowedTables()).isEmpty();
            assertThat(allowlist.deniedColumns()).isEmpty();
            // 허용목록 비활성 → 어떤 입력도 통과
            allowlist.ensureAllowed("any_table", "any_col");
        }
    }
}
