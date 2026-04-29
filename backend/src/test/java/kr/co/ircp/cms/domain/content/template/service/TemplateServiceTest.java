package kr.co.ircp.cms.domain.content.template.service;

import kr.co.ircp.cms.domain.content.template.dto.TemplateRequest;
import kr.co.ircp.cms.domain.content.template.dto.TemplateResponse;
import kr.co.ircp.cms.domain.content.template.entity.Template;
import kr.co.ircp.cms.domain.content.template.exception.TemplateInUseException;
import kr.co.ircp.cms.domain.content.template.exception.TemplateMissingSlotException;
import kr.co.ircp.cms.domain.content.template.mapper.TemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * TemplateService RED 단계 테스트.
 * REQ-CONTENT-004-D: 템플릿 관리 (슬롯 검증, 비활성화 가드)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateService RED 테스트 (REQ-CONTENT-004-D)")
class TemplateServiceTest {

    @Mock
    private TemplateMapper templateMapper;

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new TemplateServiceImpl(templateMapper);
    }

    private Template stubTemplate(long id, String code, String htmlTemplate) {
        return Template.builder()
                .id(id)
                .code(code)
                .name("템플릿 " + code)
                .layoutType("FULL")
                .htmlTemplate(htmlTemplate)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private TemplateRequest stubRequest(String htmlTemplate) {
        return new TemplateRequest(
                "TPL_001",
                "기본 템플릿",
                "FULL",
                htmlTemplate,
                null,
                null,
                "테스트용 템플릿"
        );
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-004-D-1: 템플릿 등록 — {{CONTENT}} 슬롯 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("{{CONTENT}} 슬롯 없는 템플릿 등록 시 TemplateMissingSlotException 발생")
    void shouldRegisterTemplateWithSlotValidation() {
        // Arrange — {{CONTENT}} 슬롯 미포함 HTML
        TemplateRequest request = stubRequest("<html><body><p>내용 없음</p></body></html>");

        // Act & Assert
        assertThatThrownBy(() -> templateService.createTemplate(request))
                .isInstanceOf(TemplateMissingSlotException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-004-D-3: 사용 중인 템플릿 비활성화 금지
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("사용 중인 페이지가 존재하는 템플릿 비활성화 시 TemplateInUseException 발생")
    void shouldRejectTemplateInactivationWhenInUse() {
        // Arrange
        Template template = stubTemplate(1L, "TPL_001", "<html>{{CONTENT}}</html>");
        when(templateMapper.findById(1L)).thenReturn(Optional.of(template));
        // 해당 템플릿을 사용 중인 page 3개 존재
        when(templateMapper.countPagesByTemplateId(1L)).thenReturn(3L);

        // Act & Assert
        assertThatThrownBy(() -> templateService.changeStatus(1L, "INACTIVE"))
                .isInstanceOf(TemplateInUseException.class);
    }
}
