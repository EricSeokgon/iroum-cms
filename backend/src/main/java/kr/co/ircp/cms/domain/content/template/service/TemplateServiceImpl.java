package kr.co.ircp.cms.domain.content.template.service;

import kr.co.ircp.cms.domain.content.template.dto.TemplateRequest;
import kr.co.ircp.cms.domain.content.template.dto.TemplateResponse;
import kr.co.ircp.cms.domain.content.template.mapper.TemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 템플릿 서비스 구현체.
 * REQ-CONTENT-004-D: 템플릿 관리
 *
 * // @MX:NOTE: [AUTO] RED 단계 골격. Step 2 GREEN에서 실제 구현.
 * // @MX:TODO: [AUTO] Step 2 GREEN에서 UnsupportedOperationException 제거 후 실제 로직 채움
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateServiceImpl implements TemplateService {

    private final TemplateMapper templateMapper;

    @Override
    public List<TemplateResponse> listTemplates() {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    public TemplateResponse getTemplate(Long id) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateRequest request) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public TemplateResponse updateTemplate(Long id, TemplateRequest request) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public TemplateResponse changeStatus(Long id, String status) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }
}
