package kr.co.ircp.cms.infra.ml;

/**
 * ML 추론 서비스 호출 실패 예외.
 *
 * <p>SPEC-CMS-AI-001 — 타임아웃/네트워크/5xx 등 ML 서비스 호출 실패 시 던진다.
 * 호출부는 Resilience4j 폴백 또는 FALLBACK 상태 적재로 대응한다.
 */
public class MlServiceException extends RuntimeException {

    public MlServiceException(String message) {
        super(message);
    }

    public MlServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
