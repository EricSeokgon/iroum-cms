package kr.co.ircp.cms.infra.kosha;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KOSHA OpenAPI 연동 설정.
 *
 * <p>REQ-SAFETY-001-D-4: 공공데이터포털(data.go.kr) KOSHA 산재 사례 API 호출 설정.
 * 운영 환경에서는 반드시 환경변수 KOSHA_API_KEY로 주입한다.
 *
 * <pre>
 * kosha:
 *   api:
 *     base-url: http://apis.data.go.kr/B552015/OSHIS_SAFE_ACCIDENT
 *     service-key: ${KOSHA_API_KEY}
 *     page-size: 100
 *     connect-timeout-ms: 5000
 *     read-timeout-ms: 10000
 * </pre>
 */
@ConfigurationProperties(prefix = "kosha.api")
public record KoshaApiProperties(
        /** 공공데이터포털 KOSHA API base URL */
        String baseUrl,
        /** 공공데이터포털 발급 서비스 키 */
        String serviceKey,
        /** 페이지당 레코드 수 (최대 100) */
        int pageSize,
        /** 연결 타임아웃 (ms) */
        int connectTimeoutMs,
        /** 읽기 타임아웃 (ms) */
        int readTimeoutMs
) {
    public KoshaApiProperties {
        if (pageSize <= 0) pageSize = 100;
        if (connectTimeoutMs <= 0) connectTimeoutMs = 5_000;
        if (readTimeoutMs <= 0) readTimeoutMs = 10_000;
    }
}
