package kr.co.ircp.cms.domain.ai.exception;

import java.util.UUID;

/**
 * AI 시뮬레이션 세션 미존재/만료 예외.
 *
 * <p>SPEC-CMS-AI-001 — sessionId가 없거나 expires_at &lt; now() 인 경우.
 * GlobalExceptionHandler가 HTTP 404 + code=AI_SIMULATION_NOT_FOUND 로 매핑한다.
 */
public class AiSimulationNotFoundException extends RuntimeException {

    public static final String CODE = "AI_SIMULATION_NOT_FOUND";

    public AiSimulationNotFoundException(UUID sessionId) {
        super("시뮬레이션 세션을 찾을 수 없거나 만료되었습니다: " + sessionId);
    }
}
