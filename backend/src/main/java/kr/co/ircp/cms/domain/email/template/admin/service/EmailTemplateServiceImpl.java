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
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * 이메일 템플릿 관리 서비스 구현체.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-001~005, 020, 021.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-EMAIL-TEMPLATE-001 — CRUD + 중복검출 + 미리보기/테스트발송(본인 이메일 고정)
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateServiceImpl implements EmailTemplateService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("OTP", "QNA_ANSWER", "PASSWORD_RESET", "ADMIN_NOTIFICATION", "CUSTOM");

    private final EmailTemplateMapper templateMapper;
    private final EmailTemplateRenderer renderer;
    private final EmailTemplateSendLogService sendLogService;
    private final UserMapper userMapper;
    private final JavaMailSender mailSender;
    private final EmailEncryptionService emailEncryptionService;

    @Override
    @Transactional
    public EmailTemplateResponse create(EmailTemplateCreateRequest request, Long actorUserId) {
        validateType(request.templateType());
        String language = request.languageOrDefault();
        if (templateMapper.existsByCodeAndLanguage(request.code(), language, null)) {
            throw new DuplicateEmailTemplateException(
                    "이미 존재하는 템플릿입니다: code=" + request.code() + ", language=" + language);
        }
        EmailTemplate template = EmailTemplate.builder()
                .code(request.code())
                .name(request.name())
                .templateType(request.templateType())
                .language(language)
                .subject(request.subject())
                .bodyHtml(request.bodyHtml())
                .bodyText(request.bodyText())
                .variables(request.variables())
                .isActive(request.activeOrDefault())
                .createdBy(actorUserId)
                .updatedBy(actorUserId)
                .build();
        templateMapper.insert(template);
        return EmailTemplateResponse.from(template);
    }

    @Override
    @Transactional(readOnly = true)
    public EmailTemplateResponse get(Long id) {
        return EmailTemplateResponse.from(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<EmailTemplateResponse> list(EmailTemplateSearchCriteria criteria) {
        long total = templateMapper.countAll(criteria);
        var content = templateMapper.findAll(criteria).stream()
                .map(EmailTemplateResponse::from)
                .toList();
        return new PagedResponse<>(content, criteria.page(), criteria.effectiveSize(), total);
    }

    @Override
    @Transactional
    public EmailTemplateResponse update(Long id, EmailTemplateUpdateRequest request, Long actorUserId) {
        validateType(request.templateType());
        EmailTemplate existing = findOrThrow(id);
        existing.setName(request.name());
        existing.setTemplateType(request.templateType());
        existing.setSubject(request.subject());
        existing.setBodyHtml(request.bodyHtml());
        existing.setBodyText(request.bodyText());
        existing.setVariables(request.variables());
        existing.setIsActive(request.activeOrDefault());
        existing.setUpdatedBy(actorUserId);
        templateMapper.update(existing);
        return EmailTemplateResponse.from(findOrThrow(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findOrThrow(id);
        templateMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public RenderResult preview(Long id, Map<String, Object> sampleVars) {
        EmailTemplate template = findOrThrow(id);
        // 미리보기는 실발송 로그를 생성하지 않는다 (REQ-ET-020).
        return renderer.render(template.getSubject(), template.getBodyHtml(),
                template.getBodyText(), sampleVars, template.getVariables());
    }

    @Override
    @Transactional
    public void testSend(Long id, Long actorUserId, Map<String, Object> sampleVars) {
        EmailTemplate template = findOrThrow(id);
        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw new TemplateInactiveException("비활성 템플릿은 테스트 발송할 수 없습니다: id=" + id);
        }
        RenderResult rendered = renderer.render(template.getSubject(), template.getBodyHtml(),
                template.getBodyText(), sampleVars, template.getVariables());

        String adminEmail = resolveAdminEmail(actorUserId);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setSubject(rendered.subject());
            message.setText(rendered.bodyText() != null ? rendered.bodyText() : rendered.bodyHtml());
            mailSender.send(message);
            sendLogService.record(template.getId(), template.getCode(), adminEmail,
                    rendered.subject(), "SUCCESS", null);
        } catch (Exception e) {
            sendLogService.record(template.getId(), template.getCode(), adminEmail,
                    rendered.subject(), "FAILED", e.getMessage());
            throw e;
        }
    }

    private EmailTemplate findOrThrow(Long id) {
        return templateMapper.findById(id)
                .orElseThrow(() -> new EmailTemplateNotFoundException("템플릿을 찾을 수 없습니다: id=" + id));
    }

    private void validateType(String type) {
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("허용되지 않은 template_type: " + type);
        }
    }

    /** 요청 관리자 본인의 이메일을 복호화하여 반환한다 (REQ-ET-021 — 수신자 고정). */
    private String resolveAdminEmail(Long actorUserId) {
        if (actorUserId == null) {
            throw new IllegalStateException("테스트 발송 요청 관리자를 식별할 수 없습니다");
        }
        User user = userMapper.findById(actorUserId)
                .orElseThrow(() -> new IllegalStateException("관리자 계정을 찾을 수 없습니다: " + actorUserId));
        if (user.getEmailEncrypted() != null && user.getEmailEncrypted().length > 0) {
            EncryptedEmail enc = new EncryptedEmail(
                    user.getEmailEncrypted(), user.getEmailIv(),
                    user.getEmailTag(), user.getEmailKeyVersion());
            return emailEncryptionService.decrypt(enc);
        }
        return user.getEmail();
    }
}
