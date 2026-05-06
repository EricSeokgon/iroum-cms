package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.ReportCreateRequest;
import kr.co.ircp.cms.domain.safety.dto.ReportDetail;
import kr.co.ircp.cms.domain.safety.dto.ReportSummary;

import java.util.UUID;

/**
 * 가이드라인 자동 생성 서비스.
 * REQ-SAFETY-003
 */
public interface SafetyGuidelineService {

    /**
     * 본인 회사 프로필 + 매칭 결과 + 템플릿을 결합하여 보고서 생성.
     * REQ-SAFETY-003-D
     */
    ReportDetail generateReport(Long companyId, ReportCreateRequest request);

    ReportDetail getReport(UUID uuid, boolean isAdmin, Long companyId);

    String getReportPdfPath(UUID uuid, boolean isAdmin, Long companyId);

    PageResponse<ReportSummary> listMyReports(Long companyId, int page, int size);

    PageResponse<ReportSummary> listAllReports(int page, int size);
}
