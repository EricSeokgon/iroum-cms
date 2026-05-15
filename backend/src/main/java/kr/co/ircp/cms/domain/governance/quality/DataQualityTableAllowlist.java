package kr.co.ircp.cms.domain.governance.quality;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 데이터 품질 측정 대상 테이블·컬럼 명시적 허용목록 (HIGH-5 보안 강화).
 *
 * <p>SPEC-CMS-SECURITY-HIGH-5 — DataQualityMapper.xml 의 ${} 인터폴레이션
 * 제거(매퍼 measure* API 폐기)와 함께 도입한 2차 방어선.
 *
 * <p>이전에는 {@link SafeIdentifierValidator} 만으로 information_schema 존재 여부
 * 검증을 수행하였으나, 운영자가 실수로 민감 테이블(예: users, refresh_tokens)을
 * 룰 대상 테이블로 등록할 경우 그대로 통과하는 한계가 존재했다.
 *
 * <p>본 클래스는 다음 두 단계 추가 게이트를 제공한다.
 * <ol>
 *   <li>application.yml 의 {@code iroum.governance.quality.allowed-tables} 설정값
 *       (콤마 구분) 에 등록된 테이블만 허용. 빈 값이면 게이트 비활성(기존 동작 유지).</li>
 *   <li>application.yml 의 {@code iroum.governance.quality.denied-columns}
 *       (전역 차단 컬럼) 에 등록된 컬럼은 어떤 테이블에서도 측정 대상 금지.</li>
 * </ol>
 *
 * <p>활용처: 신규 측정 룰을 도입할 때 {@link #ensureAllowed(String, String)} 를
 * 먼저 호출한 뒤 {@link SafeIdentifierValidator} 로 information_schema 검증을 수행.
 */
// @MX:ANCHOR: [AUTO] DataQualityTableAllowlist — 품질 측정 SQL 인젝션 2차 방어선
// @MX:REASON: 5개 QualityChecker(NullRatio/Range/IQR/Unique/Freshness)가 check() 진입 시 ensureAllowed() 호출
//             — 허용목록 미등록 테이블 또는 전역 차단 컬럼 접근을 IllegalArgumentException으로 차단
// @MX:SPEC: SPEC-CMS-SECURITY-HIGH-5
@Component
public class DataQualityTableAllowlist {

    private final Set<String> allowedTables;
    private final Set<String> deniedColumns;

    public DataQualityTableAllowlist(
            @Value("${iroum.governance.quality.allowed-tables:}") List<String> allowedTables,
            @Value("${iroum.governance.quality.denied-columns:password_hash,refresh_token,access_token,otp_code_hash}")
                    List<String> deniedColumns) {
        this.allowedTables = allowedTables == null
                ? Collections.emptySet()
                : new LinkedHashSet<>(allowedTables);
        this.deniedColumns = deniedColumns == null
                ? Collections.emptySet()
                : new LinkedHashSet<>(deniedColumns);
    }

    /**
     * 측정 대상 (테이블, 컬럼) 허용 여부 검증. 위반 시 IllegalArgumentException.
     *
     * @param tableName  대상 테이블 (null/blank 금지)
     * @param columnName 대상 컬럼  (FRESHNESS 룰처럼 null 허용)
     */
    public void ensureAllowed(String tableName, String columnName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Target table is required for data quality measurement.");
        }
        // 허용목록이 비어 있으면 게이트 자체를 비활성(레거시 호환).
        if (!allowedTables.isEmpty() && !allowedTables.contains(tableName)) {
            throw new IllegalArgumentException(
                    "Table not in data-quality allowlist: " + tableName);
        }
        if (columnName != null && !columnName.isBlank() && deniedColumns.contains(columnName)) {
            throw new IllegalArgumentException(
                    "Column is globally denied for data-quality measurement: " + columnName);
        }
    }

    /** 테스트·관리 도구용 — 현재 허용 테이블 스냅샷 반환. */
    public Set<String> allowedTables() {
        return Collections.unmodifiableSet(allowedTables);
    }

    /** 테스트·관리 도구용 — 현재 차단 컬럼 스냅샷 반환. */
    public Set<String> deniedColumns() {
        return Collections.unmodifiableSet(deniedColumns);
    }
}
