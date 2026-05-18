package kr.co.ircp.cms.domain.ai.exception;

/**
 * AI 예측 로그 미존재 예외.
 *
 * <p>SPEC-CMS-AI-001 — risk-score explain 시 predictionId에 해당하는 로그가 없는 경우.
 * GlobalExceptionHandler가 HTTP 404 + code=AI_PREDICTION_NOT_FOUND 로 매핑한다.
 */
public class AiPredictionNotFoundException extends RuntimeException {

    public static final String CODE = "AI_PREDICTION_NOT_FOUND";

    public AiPredictionNotFoundException(Long predictionId) {
        super("예측 로그를 찾을 수 없습니다: " + predictionId);
    }
}
