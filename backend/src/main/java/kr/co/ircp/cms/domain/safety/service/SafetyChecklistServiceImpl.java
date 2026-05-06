package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.CheckResultRequest;
import kr.co.ircp.cms.domain.safety.dto.CheckResultResponse;
import kr.co.ircp.cms.domain.safety.dto.ChecklistStatsResponse;
import kr.co.ircp.cms.domain.safety.entity.CompanySafetyProfile;
import kr.co.ircp.cms.domain.safety.entity.SafetyCheckResult;
import kr.co.ircp.cms.domain.safety.entity.SafetyChecklistItem;
import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineReport;
import kr.co.ircp.cms.domain.safety.exception.SafetyChecklistItemNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyProfileNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyReportNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.CompanySafetyProfileMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyCheckResultMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyChecklistItemMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyGuidelineReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 체크리스트 추적 서비스 구현.
 * REQ-SAFETY-004
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyChecklistServiceImpl implements SafetyChecklistService {

    private final SafetyGuidelineReportMapper reportMapper;
    private final SafetyChecklistItemMapper itemMapper;
    private final SafetyCheckResultMapper checkResultMapper;
    private final CompanySafetyProfileMapper profileMapper;

    @Override
    public List<CheckResultResponse> getChecklistByReport(UUID reportUuid, boolean isAdmin, Long companyId) {
        SafetyGuidelineReport report = reportMapper.findByUuid(reportUuid)
                .orElseThrow(() -> new SafetyReportNotFoundException(reportUuid));
        ensureAccess(report, isAdmin, companyId);
        return checkResultMapper.findChecklistWithStatusByReportId(report.getId());
    }

    @Override
    @Transactional
    public CheckResultResponse upsertCheckResult(UUID reportUuid, Long itemId,
                                                 CheckResultRequest request,
                                                 Long actorUserId, boolean isAdmin, Long companyId) {
        SafetyGuidelineReport report = reportMapper.findByUuid(reportUuid)
                .orElseThrow(() -> new SafetyReportNotFoundException(reportUuid));
        ensureAccess(report, isAdmin, companyId);

        SafetyChecklistItem item = itemMapper.findById(itemId)
                .orElseThrow(() -> new SafetyChecklistItemNotFoundException(itemId));

        SafetyCheckResult upsert = SafetyCheckResult.builder()
                .reportId(report.getId())
                .itemId(itemId)
                .checkedBy(actorUserId)
                .status(request.status())
                .evidenceText(request.evidenceText())
                .evidenceAttachmentUuid(request.evidenceAttachmentUuid())
                .build();
        checkResultMapper.upsert(upsert);

        SafetyCheckResult saved = checkResultMapper.findByReportIdAndItemId(report.getId(), itemId).orElse(upsert);
        return new CheckResultResponse(
                itemId, item.getCategory(), item.getItemText(), item.getSeverity(),
                saved.getStatus(), saved.getEvidenceText(), saved.getEvidenceAttachmentUuid(),
                saved.getCheckedBy(), saved.getCheckedAt()
        );
    }

    @Override
    public ChecklistStatsResponse getOverallStats() {
        long done = checkResultMapper.countDoneAcrossAll();
        long inProgress = checkResultMapper.countInProgressAcrossAll();
        long blocked = checkResultMapper.countBlockedAcrossAll();
        long na = checkResultMapper.countNaAcrossAll();
        long totalReports = checkResultMapper.countTotalReports();
        long totalItems = checkResultMapper.countTotalChecklistItems();
        long totalResults = checkResultMapper.countTotalCheckResults();
        double rate = totalResults == 0 ? 0.0 : (double) done / (double) totalResults;
        return new ChecklistStatsResponse(totalReports, totalItems, done, inProgress, blocked, na, rate);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private void ensureAccess(SafetyGuidelineReport report, boolean isAdmin, Long companyId) {
        if (isAdmin) return;
        CompanySafetyProfile profile = profileMapper.findByCompanyId(companyId)
                .orElseThrow(() -> new SafetyProfileNotFoundException(companyId));
        if (!profile.getId().equals(report.getCompanyProfileId())) {
            throw new AccessDeniedException("본인 회사 프로필 보고서만 조회 가능합니다.");
        }
    }
}
