package kr.co.ircp.cms.domain.governance.quality;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 데이터 품질 룰 검사 전략 인터페이스.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006~007 — NULL_RATIO/RANGE/IQR/UNIQUE/FRESHNESS 5종 룰.
 *
 * <p>각 구현체는 {@link DataQualityRule}을 받아 PostgreSQL 측정 SQL을 실행하고
 * {@link QualityCheckResult}로 결과(측정값·위반 여부·상세)를 반환한다.
 */
// @MX:ANCHOR: [AUTO] QualityChecker — 품질 룰 5종 dispatch 진입점 (5개 구현체에서 fan_in >= 3)
// @MX:REASON: NULL_RATIO/RANGE/IQR/UNIQUE/FRESHNESS 룰의 공통 SPI. 변경 시 5개 구현체 모두 영향
// @MX:SPEC: SPEC-CMS-009#REQ-DATA-006
public interface QualityChecker {

    /**
     * 룰 타입 식별자 (NULL_RATIO/RANGE/IQR/UNIQUE/FRESHNESS).
     */
    String supportedType();

    /**
     * 룰을 실행하고 결과를 반환한다.
     *
     * @param rule 검사 대상 룰
     * @param jdbc PostgreSQL JdbcTemplate (parameterized 실행용)
     * @return 측정값·위반 여부·상세 설명
     */
    QualityCheckResult check(DataQualityRule rule, JdbcTemplate jdbc);
}
