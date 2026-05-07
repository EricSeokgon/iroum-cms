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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TemplateService 단위 테스트.
 * REQ-CONTENT-004-D: 템플릿 관리 (슬롯 검증, 비활성화 가드)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateService 단위 테스트 (REQ-CONTENT-004-D)")
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
    // listTemplates()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("listTemplates() — 모든 템플릿 반환")
    void listTemplates_returnsAll() {
        // given
        when(templateMapper.findAll()).thenReturn(List.of(
                stubTemplate(1L, "TPL_001", "<html>{{CONTENT}}</html>"),
                stubTemplate(2L, "TPL_002", "<body>{{CONTENT}}</body>")));

        // when
        List<TemplateResponse> result = templateService.listTemplates();

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TemplateResponse::code)
                .containsExactly("TPL_001", "TPL_002");
    }

    @Test
    @DisplayName("listTemplates() — 빈 결과")
    void listTemplates_empty() {
        // given
        when(templateMapper.findAll()).thenReturn(List.of());

        // when
        List<TemplateResponse> result = templateService.listTemplates();

        // then
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // getTemplate()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getTemplate() — 존재하면 TemplateResponse 반환")
    void getTemplate_returns_response() {
        // given
        when(templateMapper.findById(1L))
                .thenReturn(Optional.of(stubTemplate(1L, "TPL_001", "<html>{{CONTENT}}</html>")));

        // when
        TemplateResponse result = templateService.getTemplate(1L);

        // then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.code()).isEqualTo("TPL_001");
    }

    @Test
    @DisplayName("getTemplate() — 존재하지 않으면 IllegalArgumentException")
    void getTemplate_throws_when_not_found() {
        // given
        when(templateMapper.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> templateService.getTemplate(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    // ──────────────────────────────────────────────
    // createTemplate()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("createTemplate() — {{CONTENT}} 슬롯 포함 시 정상 INSERT")
    void createTemplate_validSlot() {
        // given
        TemplateRequest req = stubRequest("<html><body>{{CONTENT}}</body></html>");

        // when
        TemplateResponse result = templateService.createTemplate(req);

        // then
        verify(templateMapper).insert(any(Template.class));
        assertThat(result.code()).isEqualTo("TPL_001");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("createTemplate() — {{CONTENT}} 슬롯 없는 템플릿이면 TemplateMissingSlotException")
    void createTemplate_missingSlot_throws() {
        // given
        TemplateRequest request = stubRequest("<html><body><p>내용 없음</p></body></html>");

        // when / then
        assertThatThrownBy(() -> templateService.createTemplate(request))
                .isInstanceOf(TemplateMissingSlotException.class);
        verify(templateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createTemplate() — htmlTemplate null이면 TemplateMissingSlotException")
    void createTemplate_nullHtml_throws() {
        // given
        TemplateRequest request = stubRequest(null);

        // when / then
        assertThatThrownBy(() -> templateService.createTemplate(request))
                .isInstanceOf(TemplateMissingSlotException.class);
    }

    @Test
    @DisplayName("createTemplate() — INSERT 시 status=ACTIVE 자동 설정")
    void createTemplate_setsActiveStatus() {
        // given
        TemplateRequest req = stubRequest("<html>{{CONTENT}}</html>");

        // when
        templateService.createTemplate(req);

        // then
        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        verify(templateMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getLayoutType()).isEqualTo("FULL");
    }

    // ──────────────────────────────────────────────
    // updateTemplate()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("updateTemplate() — 존재하지 않으면 IllegalArgumentException")
    void updateTemplate_throws_when_not_found() {
        // given
        when(templateMapper.findById(99L)).thenReturn(Optional.empty());
        TemplateRequest req = stubRequest("<html>{{CONTENT}}</html>");

        // when / then
        assertThatThrownBy(() -> templateService.updateTemplate(99L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateTemplate() — {{CONTENT}} 슬롯 없으면 TemplateMissingSlotException")
    void updateTemplate_missingSlot_throws() {
        // given
        Template existing = stubTemplate(1L, "TPL_001", "<html>{{CONTENT}}</html>");
        when(templateMapper.findById(1L)).thenReturn(Optional.of(existing));
        TemplateRequest req = stubRequest("<html>슬롯 없음</html>");

        // when / then
        assertThatThrownBy(() -> templateService.updateTemplate(1L, req))
                .isInstanceOf(TemplateMissingSlotException.class);
        verify(templateMapper, never()).update(any());
    }

    @Test
    @DisplayName("updateTemplate() — 정상 흐름에서 mapper.update 호출")
    void updateTemplate_happyPath() {
        // given
        Template existing = stubTemplate(1L, "TPL_001", "<html>{{CONTENT}}</html>");
        when(templateMapper.findById(1L)).thenReturn(Optional.of(existing));
        TemplateRequest req = new TemplateRequest(
                "TPL_001", "수정된 이름", "SIDEBAR_LEFT",
                "<div>{{CONTENT}}</div>", "[]", "[]", "수정 설명");

        // when
        TemplateResponse result = templateService.updateTemplate(1L, req);

        // then
        verify(templateMapper).update(any(Template.class));
        assertThat(result.name()).isEqualTo("수정된 이름");
        assertThat(result.layoutType()).isEqualTo("SIDEBAR_LEFT");
    }

    // ──────────────────────────────────────────────
    // changeStatus()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("changeStatus() — 존재하지 않으면 IllegalArgumentException")
    void changeStatus_throws_when_not_found() {
        // given
        when(templateMapper.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> templateService.changeStatus(99L, "INACTIVE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("changeStatus() — INACTIVE 전환 시 사용 중인 page 있으면 TemplateInUseException")
    void changeStatus_inactiveWithUsage_throws() {
        // given
        Template template = stubTemplate(1L, "TPL_001", "<html>{{CONTENT}}</html>");
        when(templateMapper.findById(1L)).thenReturn(Optional.of(template));
        when(templateMapper.countPagesByTemplateId(1L)).thenReturn(3L);

        // when / then
        assertThatThrownBy(() -> templateService.changeStatus(1L, "INACTIVE"))
                .isInstanceOf(TemplateInUseException.class);
        verify(templateMapper, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("changeStatus() — INACTIVE 전환 시 사용 중인 page 없으면 정상 처리")
    void changeStatus_inactiveWithoutUsage_succeeds() {
        // given
        Template template = stubTemplate(1L, "TPL_001", "<html>{{CONTENT}}</html>");
        when(templateMapper.findById(1L)).thenReturn(Optional.of(template));
        when(templateMapper.countPagesByTemplateId(1L)).thenReturn(0L);

        // when
        TemplateResponse result = templateService.changeStatus(1L, "INACTIVE");

        // then
        verify(templateMapper).updateStatus(1L, "INACTIVE");
        assertThat(result.status()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("changeStatus() — ACTIVE 전환 시 사용 중인 page 검증 생략")
    void changeStatus_activeNoUsageCheck() {
        // given
        Template template = stubTemplate(1L, "TPL_001", "<html>{{CONTENT}}</html>");
        when(templateMapper.findById(1L)).thenReturn(Optional.of(template));

        // when
        TemplateResponse result = templateService.changeStatus(1L, "ACTIVE");

        // then — countPagesByTemplateId 호출 안 됨
        verify(templateMapper, never()).countPagesByTemplateId(anyLong());
        verify(templateMapper).updateStatus(1L, "ACTIVE");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }
}
