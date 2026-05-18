package kr.co.ircp.cms.domain.ai.exception;

/**
 * AI 시뮬레이션 레이트리밋 초과 예외.
 *
 * <p>SPEC-CMS-AI-001 — ip-hash 기준 1시간 30회 초과 시 발생.
 * GlobalExceptionHandler가 HTTP 429 + code=AI_RATE_LIMIT_EXCEEDED 로 매핑한다.
 */
public class AiRateLimitExceededException extends RuntimeException {

    public static final String CODE = "AI_RATE_LIMIT_EXCEEDED";

    public AiRateLimitExceededException(int limitPerHour) {
        super("시뮬레이션 요청 한도(시간당 " + limitPerHour + "회)를 초과했습니다");
    }
}
