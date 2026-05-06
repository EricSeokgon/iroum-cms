package kr.co.ircp.cms.domain.policy.program.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramCreateRequest;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramDetail;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramSummary;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramSyncResult;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramUpdateRequest;

/**
 * 정책사업 마스터 서비스.
 * REQ-POLICY-001
 */
public interface PolicyProgramService {

    PageResponse<PolicyProgramSummary> listPrograms(
            String status, String industry, String region, String keyword,
            int page, int size);

    PolicyProgramDetail getProgram(Long id);

    PolicyProgramDetail createProgram(PolicyProgramCreateRequest request);

    PolicyProgramDetail updateProgram(Long id, PolicyProgramUpdateRequest request);

    void deleteProgram(Long id);

    /** K-Startup mock 동기화 — 외부 API 모킹 + import_warnings 적재. */
    PolicyProgramSyncResult syncFromExternal(String sourceCode);
}
