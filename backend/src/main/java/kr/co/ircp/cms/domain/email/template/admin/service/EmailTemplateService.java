package kr.co.ircp.cms.domain.email.template.admin.service;

import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateCreateRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateSearchCriteria;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateUpdateRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.PagedResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;

import java.util.Map;

/**
 * 이메일 템플릿 관리 서비스.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-001~005, 020, 021.
 */
public interface EmailTemplateService {

    EmailTemplateResponse create(EmailTemplateCreateRequest request, Long actorUserId);

    EmailTemplateResponse get(Long id);

    PagedResponse<EmailTemplateResponse> list(EmailTemplateSearchCriteria criteria);

    EmailTemplateResponse update(Long id, EmailTemplateUpdateRequest request, Long actorUserId);

    void delete(Long id);

    /** 실발송 없이 렌더링만 수행한다 (REQ-ET-020). */
    RenderResult preview(Long id, Map<String, Object> sampleVars);

    /** 요청 관리자 본인 이메일로만 테스트 발송한다 (REQ-ET-021). */
    void testSend(Long id, Long actorUserId, Map<String, Object> sampleVars);
}
