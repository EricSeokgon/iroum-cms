package kr.co.ircp.cms.infra.kosha;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * KOSHA 산재 사례 OpenAPI 클라이언트.
 *
 * <p>REQ-SAFETY-001-D-4: 공공데이터포털 OSHIS(산업안전보건정보시스템) API 호출.
 * {@code kosha.api.service-key} 설정 시에만 활성화된다.
 * 키 미설정 환경(로컬·테스트)에서는 빈이 등록되지 않으므로 서비스는 mock 결과를 반환한다.
 *
 * // @MX:NOTE: [AUTO] service-key 없으면 빈 비활성화 — 로컬/테스트에서 실 호출 차단
 * // @MX:SPEC: REQ-SAFETY-001-D-4
 */
@Component
@ConditionalOnExpression("!'${kosha.api.service-key:}'.empty")
@EnableConfigurationProperties(KoshaApiProperties.class)
public class KoshaApiClient {

    private static final String ITEMS_PATH = "/getAccidentList";

    private final KoshaApiProperties props;
    private final RestTemplate restTemplate;

    public KoshaApiClient(KoshaApiProperties props, RestTemplateBuilder builder) {
        this.props = props;
        // SimpleClientHttpRequestFactory 명시 — Spring Boot 3.5.x 기본 JdkClientHttpRequestFactory 우회
        Duration connectTimeout = Duration.ofMillis(props.connectTimeoutMs());
        Duration readTimeout = Duration.ofMillis(props.readTimeoutMs());
        this.restTemplate = builder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(connectTimeout);
                    factory.setReadTimeout(readTimeout);
                    return factory;
                })
                .build();
    }

    /**
     * 산재 사례 목록 조회 (페이지 단위).
     *
     * @param pageNo 1-based 페이지 번호
     * @return 단일 페이지 사례 목록 (빈 리스트 가능)
     */
    public List<KoshaIncidentItem> fetchPage(int pageNo) {
        String url = UriComponentsBuilder.fromHttpUrl(props.baseUrl() + ITEMS_PATH)
                .queryParam("serviceKey", props.serviceKey())
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", props.pageSize())
                .queryParam("resultType", "json")
                .build(false)
                .toUriString();

        ResponseEntity<KoshaIncidentItem[]> response =
                restTemplate.getForEntity(url, KoshaIncidentItem[].class);

        if (response.getBody() == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(response.getBody());
    }
}
