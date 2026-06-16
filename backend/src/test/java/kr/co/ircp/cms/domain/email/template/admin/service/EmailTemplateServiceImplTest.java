package kr.co.ircp.cms.domain.email.template.admin.service;

import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateCreateRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateSearchCriteria;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateUpdateRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.PagedResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.entity.EmailTemplate;
import kr.co.ircp.cms.domain.email.template.admin.exception.DuplicateEmailTemplateException;
import kr.co.ircp.cms.domain.email.template.admin.exception.EmailTemplateNotFoundException;
import kr.co.ircp.cms.domain.email.template.admin.exception.TemplateInactiveException;
import kr.co.ircp.cms.domain.email.template.admin.repository.EmailTemplateMapper;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmailTemplateServiceImpl 단위 테스트 (T3).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-001~005, 020, 021.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailTemplateServiceImpl 단위 테스트 (REQ-ET-001~005/020/021)")
class EmailTemplateServiceImplTest {

    @Mock EmailTemplateMapper templateMapper;
    @Mock EmailTemplateRenderer renderer;
    @Mock EmailTemplateSendLogService sendLogService;
    @Mock UserMapper userMapper;
    @Mock JavaMailSender mailSender;
    @Mock EmailEncryptionService emailEncryptionService;

    @InjectMocks
    EmailTemplateServiceImpl service;

    @Test
    @DisplayName("AC-ET-001: 신규 템플릿이 모든 필드와 함께 저장된다")
    void create_savesTemplate() {
        var request = new EmailTemplateCreateRequest(
                "OTP", "OTP 메일", "OTP", "ko", "제목", "<p>본문</p>", "평문",
                List.of(Map.of("name", "code", "required", true)), true);
        when(templateMapper.existsByCodeAndLanguage("OTP", "ko", null)).thenReturn(false);

        EmailTemplateResponse response = service.create(request, 7L);

        assertThat(response.code()).isEqualTo("OTP");
        assertThat(response.createdBy()).isEqualTo(7L);
        verify(templateMapper).insert(any(EmailTemplate.class));
    }

    @Test
    @DisplayName("AC-ET-001: 동일 code+language 재등록은 거부된다(409)")
    void create_rejectsDuplicate() {
        var request = new EmailTemplateCreateRequest(
                "OTP", "OTP 메일", "OTP", "ko", "제목", "<p>본문</p>", null, null, true);
        when(templateMapper.existsByCodeAndLanguage("OTP", "ko", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request, 1L))
                .isInstanceOf(DuplicateEmailTemplateException.class);
        verify(templateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("AC-ET-004: 허용되지 않은 template_type은 거부된다(400)")
    void create_rejectsInvalidType() {
        var request = new EmailTemplateCreateRequest(
                "X", "이름", "MARKETING", "ko", "제목", "<p>본문</p>", null, null, true);

        assertThatThrownBy(() -> service.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AC-ET-002: 목록 조회가 필터·페이지네이션을 적용한다")
    void list_appliesFilters() {
        var criteria = new EmailTemplateSearchCriteria("OTP", "ko", true, "key", 0, 20);
        when(templateMapper.countAll(criteria)).thenReturn(3L);
        when(templateMapper.findAll(criteria)).thenReturn(List.of(
                EmailTemplate.builder().id(1L).code("OTP").isActive(true).build()));

        PagedResponse<EmailTemplateResponse> page = service.list(criteria);

        assertThat(page.totalCount()).isEqualTo(3L);
        assertThat(page.content()).hasSize(1);
    }

    @Test
    @DisplayName("AC-ET-003: 수정 시 updatedBy가 갱신된다")
    void update_setsUpdatedBy() {
        EmailTemplate existing = EmailTemplate.builder()
                .id(1L).code("OTP").language("ko").templateType("OTP")
                .subject("old").bodyHtml("<p>old</p>").isActive(true).build();
        when(templateMapper.findById(1L)).thenReturn(Optional.of(existing));

        var request = new EmailTemplateUpdateRequest(
                "수정됨", "OTP", "새 제목", "<p>새 본문</p>", null, null, true);
        service.update(1L, request, 9L);

        verify(templateMapper).update(any(EmailTemplate.class));
        assertThat(existing.getUpdatedBy()).isEqualTo(9L);
        assertThat(existing.getName()).isEqualTo("수정됨");
    }

    @Test
    @DisplayName("수정 대상이 없으면 404")
    void update_notFound() {
        when(templateMapper.findById(99L)).thenReturn(Optional.empty());
        var request = new EmailTemplateUpdateRequest(
                "n", "OTP", "s", "<p>b</p>", null, null, true);

        assertThatThrownBy(() -> service.update(99L, request, 1L))
                .isInstanceOf(EmailTemplateNotFoundException.class);
    }

    @Test
    @DisplayName("AC-ET-003: 삭제는 템플릿만 제거한다(로그 보존은 FK ON DELETE SET NULL)")
    void delete_removesTemplate() {
        when(templateMapper.findById(1L)).thenReturn(Optional.of(
                EmailTemplate.builder().id(1L).build()));

        service.delete(1L);

        verify(templateMapper).deleteById(1L);
    }

    @Test
    @DisplayName("AC-ET-008: 미리보기는 렌더링만 하고 발송 로그를 만들지 않는다")
    void preview_doesNotCreateSendLog() {
        EmailTemplate t = EmailTemplate.builder()
                .id(1L).subject("제목").bodyHtml("<p>본문</p>").isActive(true).build();
        when(templateMapper.findById(1L)).thenReturn(Optional.of(t));
        when(renderer.render(any(), any(), any(), anyMap(), any()))
                .thenReturn(new RenderResult("제목", "<p>본문</p>", null));

        service.preview(1L, Map.of());

        verify(sendLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-ET-009: 테스트 발송은 요청 관리자 본인 이메일로만 발송되고 로그를 남긴다")
    void testSend_sendsToAdminEmailAndLogs() {
        EmailTemplate t = EmailTemplate.builder()
                .id(1L).code("OTP").subject("제목").bodyHtml("<p>본문</p>")
                .bodyText("평문").isActive(true).build();
        when(templateMapper.findById(1L)).thenReturn(Optional.of(t));
        when(renderer.render(any(), any(), any(), anyMap(), any()))
                .thenReturn(new RenderResult("제목", "<p>본문</p>", "평문"));
        User admin = new User();
        admin.setId(5L);
        admin.setEmail("admin@ircp.co.kr");
        when(userMapper.findById(5L)).thenReturn(Optional.of(admin));

        service.testSend(1L, 5L, Map.of());

        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("admin@ircp.co.kr");
        verify(sendLogService).record(eq(1L), eq("OTP"), eq("admin@ircp.co.kr"),
                eq("제목"), eq("SUCCESS"), isNull());
    }

    @Test
    @DisplayName("AC-ET-007: 비활성 템플릿 테스트 발송은 거부된다(409)")
    void testSend_rejectsInactiveTemplate() {
        EmailTemplate t = EmailTemplate.builder()
                .id(1L).code("OTP").subject("제목").bodyHtml("<p>본문</p>").isActive(false).build();
        when(templateMapper.findById(1L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.testSend(1L, 5L, Map.of()))
                .isInstanceOf(TemplateInactiveException.class);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
