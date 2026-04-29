package kr.co.ircp.cms.domain.content.template.service;

import kr.co.ircp.cms.domain.content.template.dto.TemplateRequest;
import kr.co.ircp.cms.domain.content.template.dto.TemplateResponse;

import java.util.List;

/**
 * 템플릿 서비스 인터페이스.
 * REQ-CONTENT-004-D: 템플릿 관리
 *
 * // @MX:ANCHOR: [AUTO] TemplateService — 템플릿 비즈니스 계약
 * // @MX:REASON: TemplateController, PageService에서 fan_in >= 3으로 참조
 * // @MX:SPEC: REQ-CONTENT-004-D
 */
public interface TemplateService {

    /** 템플릿 목록 조회 */
    List<TemplateResponse> listTemplates();

    /** 템플릿 단건 조회 */
    TemplateResponse getTemplate(Long id);

    /** 템플릿 등록 ({{CONTENT}} 슬롯 검증) */
    TemplateResponse createTemplate(TemplateRequest request);

    /** 템플릿 수정 */
    TemplateResponse updateTemplate(Long id, TemplateRequest request);

    /**
     * 템플릿 상태 변경 (INACTIVE 전환 시 사용 page 존재하면 409).
     * REQ-CONTENT-004-D-3
     */
    TemplateResponse changeStatus(Long id, String status);
}
