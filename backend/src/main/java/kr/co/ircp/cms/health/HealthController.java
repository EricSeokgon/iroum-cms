package kr.co.ircp.cms.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 서비스 헬스 체크 엔드포인트.
 *
 * <p>로드밸런서·오케스트레이터가 인스턴스 상태를 확인하는 데 사용된다.
 * Spring Boot Actuator(/actuator/health)와 별개로,
 * API 게이트웨이 레이어에서 간단히 호출할 수 있는 커스텀 엔드포인트를 제공한다.
 */
@Tag(name = "Health", description = "서비스 헬스 체크")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    // @MX:NOTE: [AUTO] 이 엔드포인트는 인증 없이 공개된다 — SecurityConfig에서 permitAll 처리 필요
    @Operation(summary = "헬스 체크", description = "서비스 가동 상태를 반환한다.")
    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "iroum-cms-backend",
                "version", "0.1.0-SNAPSHOT"
        );
    }
}
