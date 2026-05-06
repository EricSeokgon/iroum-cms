package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.ExportRequest;
import kr.co.ircp.cms.domain.dashboard.dto.ExportResponse;
import kr.co.ircp.cms.domain.dashboard.entity.ExportHistory;

import java.time.Instant;
import java.util.List;

/**
 * Export 서비스 인터페이스.
 * REQ-VIZ-006
 */
public interface ExportService {

    ExportResponse createExport(Long requestorId, ExportRequest req);

    ExportResponse getStatus(Long exportId, Long requestorId);

    /**
     * 다운로드 사전 검증.
     * @return 파일 경로 정보를 포함한 ExportHistory
     * @throws kr.co.ircp.cms.domain.dashboard.exception.ExportNotFoundException 미존재
     * @throws kr.co.ircp.cms.domain.dashboard.exception.ExportAccessDeniedException 권한 거부
     * @throws kr.co.ircp.cms.domain.dashboard.exception.ExportExpiredException 410 Gone
     */
    ExportHistory verifyDownload(Long exportId, Long requestorId, boolean isSuperAdmin, String signature);

    /** 본인의 export 이력 조회 (status null = 전체). */
    List<ExportResponse> listHistory(Long requestorId, String status);

    /** HMAC-SHA256 서명 생성 (테스트 용 노출). */
    String signFor(Long exportId, Instant expiresAt);
}
