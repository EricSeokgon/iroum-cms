package kr.co.ircp.cms.domain.system.code.service;

import kr.co.ircp.cms.domain.system.code.dto.CodeGroupRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeGroupResponse;

import java.util.List;

/**
 * 공통코드 그룹 서비스 인터페이스.
 * REQ-SYSTEM-004-D
 */
public interface CodeGroupService {

    CodeGroupResponse create(CodeGroupRequest request);

    CodeGroupResponse getById(Long id);

    List<CodeGroupResponse> listAll();

    CodeGroupResponse update(Long id, CodeGroupRequest request);

    /** 사용 중인 코드가 있으면 CodeGroupInUseException */
    void delete(Long id);
}
