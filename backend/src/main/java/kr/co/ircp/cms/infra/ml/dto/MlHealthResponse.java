package kr.co.ircp.cms.infra.ml.dto;

import java.util.List;

/**
 * ML 서비스 헬스 체크 응답.
 *
 * <p>SPEC-CMS-AI-001.
 */
public record MlHealthResponse(
        String status,
        List<String> loadedModels
) {
}
