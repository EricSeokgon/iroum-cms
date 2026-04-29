package kr.co.ircp.cms.domain.content.template.service;

import kr.co.ircp.cms.domain.content.template.dto.TemplateRequest;
import kr.co.ircp.cms.domain.content.template.dto.TemplateResponse;
import kr.co.ircp.cms.domain.content.template.entity.Template;
import kr.co.ircp.cms.domain.content.template.exception.TemplateInUseException;
import kr.co.ircp.cms.domain.content.template.exception.TemplateMissingSlotException;
import kr.co.ircp.cms.domain.content.template.mapper.TemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 템플릿 서비스 구현체.
 * REQ-CONTENT-004-D: 템플릿 관리 (슬롯 검증, 비활성화 가드)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateServiceImpl implements TemplateService {

    /** Mustache 콘텐츠 슬롯 마커 */
    private static final String CONTENT_SLOT = "{{CONTENT}}";

    private final TemplateMapper templateMapper;

    @Override
    public List<TemplateResponse> listTemplates() {
        return templateMapper.findAll().stream()
                .map(TemplateResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public TemplateResponse getTemplate(Long id) {
        return templateMapper.findById(id)
                .map(TemplateResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("템플릿을 찾을 수 없습니다. id=" + id));
    }

    /**
     * 템플릿 등록.
     * REQ-CONTENT-004-D-1: {{CONTENT}} 슬롯 필수 검증
     */
    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateRequest request) {
        // {{CONTENT}} 슬롯 필수 검증
        if (request.htmlTemplate() == null || !request.htmlTemplate().contains(CONTENT_SLOT)) {
            throw new TemplateMissingSlotException(CONTENT_SLOT);
        }

        Template template = Template.builder()
                .code(request.code())
                .name(request.name())
                .layoutType(request.layoutType())
                .htmlTemplate(request.htmlTemplate())
                .cssAssets(request.cssAssets())
                .jsAssets(request.jsAssets())
                .description(request.description())
                .status("ACTIVE")
                .build();

        templateMapper.insert(template);
        return TemplateResponse.from(template);
    }

    @Override
    @Transactional
    public TemplateResponse updateTemplate(Long id, TemplateRequest request) {
        Template template = templateMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("템플릿을 찾을 수 없습니다. id=" + id));

        if (request.htmlTemplate() == null || !request.htmlTemplate().contains(CONTENT_SLOT)) {
            throw new TemplateMissingSlotException(CONTENT_SLOT);
        }

        template.setCode(request.code());
        template.setName(request.name());
        template.setLayoutType(request.layoutType());
        template.setHtmlTemplate(request.htmlTemplate());
        template.setCssAssets(request.cssAssets());
        template.setJsAssets(request.jsAssets());
        template.setDescription(request.description());
        templateMapper.update(template);
        return TemplateResponse.from(template);
    }

    /**
     * 템플릿 상태 변경.
     * REQ-CONTENT-004-D-3: INACTIVE 전환 시 사용 중인 page 존재 여부 검증
     */
    @Override
    @Transactional
    public TemplateResponse changeStatus(Long id, String status) {
        Template template = templateMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("템플릿을 찾을 수 없습니다. id=" + id));

        if ("INACTIVE".equals(status)) {
            long pageCount = templateMapper.countPagesByTemplateId(id);
            if (pageCount > 0) {
                throw new TemplateInUseException(id, pageCount);
            }
        }

        templateMapper.updateStatus(id, status);
        template.setStatus(status);
        return TemplateResponse.from(template);
    }
}
