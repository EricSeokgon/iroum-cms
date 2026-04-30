package kr.co.ircp.cms.domain.system.code.service;

import kr.co.ircp.cms.domain.system.code.dto.BulkCodesResponse;
import kr.co.ircp.cms.domain.system.code.dto.CodeRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeResponse;

import java.util.List;

/**
 * 공통코드 서비스 인터페이스.
 * REQ-SYSTEM-004-D
 */
public interface CodeService {

    CodeResponse create(CodeRequest request);

    CodeResponse getById(Long id);

    /** ACTIVE 코드만 sort_order ASC */
    List<CodeResponse> listByGroup(String groupCode);

    /** 여러 그룹 일괄 조회 */
    BulkCodesResponse bulkByGroups(List<String> groupCodes);

    CodeResponse update(Long id, CodeRequest request);

    void delete(Long id);
}
