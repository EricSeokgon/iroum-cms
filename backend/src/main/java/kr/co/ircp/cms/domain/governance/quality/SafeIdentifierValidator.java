package kr.co.ircp.cms.domain.governance.quality;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 테이블·컬럼 식별자 SQL 인젝션 방어 검증.
 *
 * <p>품질 검사 SQL은 JdbcTemplate에서 ${...} 형태로 식별자를 직접 SQL 문자열에
 * 보간하므로, information_schema에 존재하는 식별자만 통과시켜야 한다.
 *
 * <p>2단계 검증:
 * 1. 정규식 패턴 매칭 (영문·숫자·언더스코어 only, 80자 이하)
 * 2. information_schema.tables / information_schema.columns 존재 여부 확인
 */
// @MX:ANCHOR: [AUTO] SafeIdentifierValidator — 5개 QualityChecker가 모두 사용 (fan_in = 5)
// @MX:REASON: SQL 인젝션 1차 방어선. 변경 시 5개 checker 모두 영향
// @MX:WARN: [AUTO] 식별자 보간(${...})은 prepared statement 우회. 검증 누락 시 인젝션 가능
// @MX:REASON: PostgreSQL DDL identifier 직접 보간 — V18 시드 외 외부 입력은 본 클래스로만 게이트
// @MX:SPEC: SPEC-CMS-009#REQ-DATA-006
@Component
public class SafeIdentifierValidator {

    /** 영문·숫자·언더스코어 only, 1~80자 (PostgreSQL identifier 한계 = 63 + margin). */
    private static final Pattern SAFE_IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,79}$");

    private final JdbcTemplate jdbc;

    public SafeIdentifierValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 테이블 식별자 검증. 안전하지 않으면 IllegalArgumentException.
     */
    public void validateTable(String tableName) {
        if (tableName == null || !SAFE_IDENT.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Unsafe table identifier: " + tableName);
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_name = ?",
                Integer.class, tableName);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Table not found in current schema: " + tableName);
        }
    }

    /**
     * 컬럼 식별자 검증. tableName은 사전 검증되어 있어야 한다.
     */
    public void validateColumn(String tableName, String columnName) {
        if (columnName == null || !SAFE_IDENT.matcher(columnName).matches()) {
            throw new IllegalArgumentException("Unsafe column identifier: " + columnName);
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class, tableName, columnName);
        if (count == null || count == 0) {
            throw new IllegalArgumentException(
                    "Column not found: " + tableName + "." + columnName);
        }
    }
}
