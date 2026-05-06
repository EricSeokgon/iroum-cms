package kr.co.ircp.cms.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import kr.co.ircp.cms.support.WebMvcTestInfraConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HealthController 단위 테스트 (TDD — RED → GREEN).
 *
 * <p>WebMvcTest 슬라이스 테스트: DB·Security 컨텍스트를 제외하고
 * 컨트롤러 레이어만 로드하여 빠르게 검증한다.
 * SecurityAutoConfiguration을 제외하여 인증 없이 헬스 엔드포인트를 테스트한다.
 */
@WebMvcTest(HealthController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import(WebMvcTestInfraConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/health — 200 OK, status=UP 반환")
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
               .andDo(print())
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"))
               .andExpect(jsonPath("$.service").value("iroum-cms-backend"))
               .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"));
    }
}
