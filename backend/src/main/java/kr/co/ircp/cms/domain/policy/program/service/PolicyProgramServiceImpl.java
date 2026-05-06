package kr.co.ircp.cms.domain.policy.program.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramCreateRequest;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramDetail;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramSummary;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramSyncResult;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramUpdateRequest;
import kr.co.ircp.cms.domain.policy.program.entity.PolicyProgram;
import kr.co.ircp.cms.domain.policy.program.exception.PolicyProgramNotFoundException;
import kr.co.ircp.cms.domain.policy.program.repository.PolicyProgramMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 정책사업 마스터 서비스 구현.
 * REQ-POLICY-001
 *
 * // @MX:NOTE: [AUTO] 외부 OpenAPI 동기화는 1차 mock 구현. v0.2 에서 K-Startup 실 호출 + Spring Retry.
 * // @MX:SPEC: REQ-POLICY-001-D-1
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyProgramServiceImpl implements PolicyProgramService {

    private final PolicyProgramMapper programMapper;

    @Override
    public PageResponse<PolicyProgramSummary> listPrograms(
            String status, String industry, String region, String keyword,
            int page, int size) {
        int offset = page * size;
        List<PolicyProgram> rows = programMapper.findFiltered(status, industry, region, keyword, offset, size);
        long total = programMapper.countFiltered(status, industry, region, keyword);
        List<PolicyProgramSummary> content = rows.stream().map(this::toSummary).collect(Collectors.toList());
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public PolicyProgramDetail getProgram(Long id) {
        PolicyProgram program = programMapper.findById(id)
                .orElseThrow(() -> new PolicyProgramNotFoundException(id));
        return toDetail(program);
    }

    @Override
    @Transactional
    public PolicyProgramDetail createProgram(PolicyProgramCreateRequest request) {
        PolicyProgram program = PolicyProgram.builder()
                .code(request.code())
                .ministry(request.ministry())
                .programName(request.programName())
                .programNameI18n(request.programNameI18n() == null ? "{}" : request.programNameI18n())
                .descriptionHtml(request.descriptionHtml())
                .targetIndustries(request.targetIndustries() == null ? List.of() : request.targetIndustries())
                .targetRegions(request.targetRegions() == null ? List.of() : request.targetRegions())
                .minEmployees(request.minEmployees())
                .maxEmployees(request.maxEmployees())
                .minRevenue(request.minRevenue())
                .maxRevenue(request.maxRevenue())
                .minBusinessAgeMonths(request.minBusinessAgeMonths())
                .maxBusinessAgeMonths(request.maxBusinessAgeMonths())
                .applicationStart(request.applicationStart())
                .applicationEnd(request.applicationEnd())
                .budgetTotal(request.budgetTotal())
                .budgetPerCompany(request.budgetPerCompany())
                .sourceUrl(request.sourceUrl())
                .status(request.status() == null ? "DRAFT" : request.status())
                .build();
        programMapper.insert(program);
        return toDetail(program);
    }

    @Override
    @Transactional
    public PolicyProgramDetail updateProgram(Long id, PolicyProgramUpdateRequest request) {
        PolicyProgram existing = programMapper.findById(id)
                .orElseThrow(() -> new PolicyProgramNotFoundException(id));

        if (request.programName() != null)            existing.setProgramName(request.programName());
        if (request.programNameI18n() != null)        existing.setProgramNameI18n(request.programNameI18n());
        if (request.descriptionHtml() != null)        existing.setDescriptionHtml(request.descriptionHtml());
        if (request.targetIndustries() != null)       existing.setTargetIndustries(request.targetIndustries());
        if (request.targetRegions() != null)          existing.setTargetRegions(request.targetRegions());
        if (request.minEmployees() != null)           existing.setMinEmployees(request.minEmployees());
        if (request.maxEmployees() != null)           existing.setMaxEmployees(request.maxEmployees());
        if (request.minRevenue() != null)             existing.setMinRevenue(request.minRevenue());
        if (request.maxRevenue() != null)             existing.setMaxRevenue(request.maxRevenue());
        if (request.minBusinessAgeMonths() != null)   existing.setMinBusinessAgeMonths(request.minBusinessAgeMonths());
        if (request.maxBusinessAgeMonths() != null)   existing.setMaxBusinessAgeMonths(request.maxBusinessAgeMonths());
        if (request.applicationStart() != null)       existing.setApplicationStart(request.applicationStart());
        if (request.applicationEnd() != null)         existing.setApplicationEnd(request.applicationEnd());
        if (request.budgetTotal() != null)            existing.setBudgetTotal(request.budgetTotal());
        if (request.budgetPerCompany() != null)       existing.setBudgetPerCompany(request.budgetPerCompany());
        if (request.sourceUrl() != null)              existing.setSourceUrl(request.sourceUrl());
        if (request.status() != null)                 existing.setStatus(request.status());

        programMapper.update(existing);
        return toDetail(existing);
    }

    @Override
    @Transactional
    public void deleteProgram(Long id) {
        PolicyProgram existing = programMapper.findById(id)
                .orElseThrow(() -> new PolicyProgramNotFoundException(id));
        programMapper.deleteById(existing.getId());
    }

    @Override
    @Transactional
    public PolicyProgramSyncResult syncFromExternal(String sourceCode) {
        // 1차 mock 구현 — 실제 운영 시 K-Startup OpenAPI 호출 + IntegrationLogInterceptor 적재
        // 본 메서드는 동기화 라이프사이클 (fetched/inserted/updated/skipped) 만 시뮬레이션한다.
        return new PolicyProgramSyncResult(
                sourceCode == null ? "K_STARTUP" : sourceCode,
                0, 0, 0, 0,
                Instant.now()
        );
    }

    // ─── 변환 ─────────────────────────────────────────────────────────────────

    PolicyProgramSummary toSummary(PolicyProgram p) {
        return new PolicyProgramSummary(
                p.getId(), p.getCode(), p.getMinistry(), p.getProgramName(),
                p.getTargetIndustries(), p.getTargetRegions(),
                p.getApplicationStart(), p.getApplicationEnd(),
                p.getStatus()
        );
    }

    PolicyProgramDetail toDetail(PolicyProgram p) {
        return new PolicyProgramDetail(
                p.getId(), p.getCode(), p.getMinistry(), p.getProgramName(),
                p.getProgramNameI18n(), p.getDescriptionHtml(),
                p.getTargetIndustries(), p.getTargetRegions(),
                p.getMinEmployees(), p.getMaxEmployees(),
                p.getMinRevenue(), p.getMaxRevenue(),
                p.getMinBusinessAgeMonths(), p.getMaxBusinessAgeMonths(),
                p.getApplicationStart(), p.getApplicationEnd(),
                p.getBudgetTotal(), p.getBudgetPerCompany(),
                p.getSourceUrl(), p.getStatus(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
