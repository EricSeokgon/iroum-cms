package kr.co.ircp.cms.support;

import kr.co.ircp.cms.domain.system.accesslog.service.AccessLogService;
import kr.co.ircp.cms.domain.system.maintenance.service.MaintenanceService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * @WebMvcTest 슬라이스에서 필요한 공통 인프라 빈을 Mock으로 제공하는 테스트 구성.
 *
 * <p>{@link kr.co.ircp.cms.domain.system.accesslog.filter.AccessLogFilter}와
 * {@link kr.co.ircp.cms.domain.system.maintenance.filter.MaintenanceFilter}는
 * {@code @Component}로 선언되어 @WebMvcTest 컨텍스트에서도 로드된다.
 * 그러나 이 필터들이 의존하는 Service 빈은 슬라이스에 포함되지 않으므로
 * 각 컨트롤러 테스트에서 별도로 Mock을 제공해야 한다.
 *
 * <p>중복을 줄이기 위해 모든 @WebMvcTest 클래스는 다음과 같이 본 구성을 임포트한다.
 * <pre>{@code
 * @Import(WebMvcTestInfraConfig.class)
 * }</pre>
 */
// @MX:NOTE: [AUTO] @WebMvcTest 슬라이스 공통 Mock — 필터 의존성 해결용
@TestConfiguration
public class WebMvcTestInfraConfig {

    @MockBean
    private AccessLogService accessLogService;

    @MockBean
    private MaintenanceService maintenanceService;
}
