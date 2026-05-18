package kr.co.ircp.cms.domain.policy.aimatch.exception;

/**
 * 추천 피드백 무결성 위반 예외.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-013 — interaction_type이 VIEWED이거나 policy_id가
 * 누락된 경우. GlobalExceptionHandler가 HTTP 400 + code=AI_FEEDBACK_INVALID로 매핑한다.
 * DB {@code chk_aprl_feedback} 제약과 일관된다.
 */
public class AiFeedbackInvalidException extends RuntimeException {

    public static final String CODE = "AI_FEEDBACK_INVALID";

    public AiFeedbackInvalidException(String message) {
        super(message);
    }
}
