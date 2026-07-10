package kr.co.ircp.cms.domain.notification.template.admin.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateCreateRequest;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplatePreviewResult;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateResponse;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateUpdateRequest;
import kr.co.ircp.cms.domain.notification.template.admin.entity.NotificationTemplate;
import kr.co.ircp.cms.domain.notification.template.admin.exception.DuplicateNotificationTemplateException;
import kr.co.ircp.cms.domain.notification.template.admin.exception.NotificationTemplateNotFoundException;
import kr.co.ircp.cms.domain.notification.template.admin.repository.NotificationTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 알림 템플릿 관리 서비스 구현체.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — CRUD + (code, language) 중복검출 + ${var} 미리보기 치환.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-NOTI-EXT-001 — notification_template CRUD + 미리보기(${var} 치환, 발송 없음)
// @MX:SPEC: SPEC-CMS-NOTI-EXT-001
@Service
@RequiredArgsConstructor
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    private final NotificationTemplateMapper mapper;

    @Override
    @Transactional
    public NotificationTemplateResponse create(NotificationTemplateCreateRequest request, Long actorUserId) {
        if (mapper.existsByCodeAndLanguage(request.code(), request.language(), null)) {
            throw new DuplicateNotificationTemplateException(
                    "이미 존재하는 알림 템플릿입니다: code=" + request.code() + ", language=" + request.language());
        }
        NotificationTemplate template = NotificationTemplate.builder()
                .code(request.code())
                .name(request.name())
                .channel(request.channel())
                .subject(request.subject())
                .bodyHtml(request.bodyHtml())
                .variables(request.variables())
                .language(request.language())
                .isActive(request.activeOrDefault())
                .emailTemplateId(request.emailTemplateId())
                .createdBy(actorUserId)
                .updatedBy(actorUserId)
                .build();
        mapper.insert(template);
        return NotificationTemplateResponse.from(template);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationTemplateResponse> getAll(Boolean isActive, int page, int size) {
        int offset = page * size;
        long total = mapper.countAll(isActive);
        var content = mapper.findAll(isActive, offset, size).stream()
                .map(NotificationTemplateResponse::from)
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationTemplateResponse getById(Long id) {
        return NotificationTemplateResponse.from(findOrThrow(id));
    }

    @Override
    @Transactional
    public NotificationTemplateResponse update(Long id, NotificationTemplateUpdateRequest request, Long actorUserId) {
        NotificationTemplate existing = findOrThrow(id);
        if (request.name() != null) {
            existing.setName(request.name());
        }
        if (request.channel() != null) {
            existing.setChannel(request.channel());
        }
        if (request.subject() != null) {
            existing.setSubject(request.subject());
        }
        if (request.bodyHtml() != null) {
            existing.setBodyHtml(request.bodyHtml());
        }
        if (request.variables() != null) {
            existing.setVariables(request.variables());
        }
        if (request.isActive() != null) {
            existing.setIsActive(request.isActive());
        }
        if (request.emailTemplateId() != null) {
            existing.setEmailTemplateId(request.emailTemplateId());
        }
        existing.setUpdatedBy(actorUserId);
        mapper.update(existing);
        return NotificationTemplateResponse.from(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findOrThrow(id);
        mapper.delete(id);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationTemplatePreviewResult previewTemplate(Long id, Map<String, String> sampleVariables) {
        NotificationTemplate template = findOrThrow(id);
        Map<String, String> vars = sampleVariables != null ? sampleVariables : Map.of();
        return new NotificationTemplatePreviewResult(
                substitute(template.getSubject(), vars),
                substitute(template.getBodyHtml(), vars));
    }

    private NotificationTemplate findOrThrow(Long id) {
        return mapper.findById(id)
                .orElseThrow(() -> new NotificationTemplateNotFoundException(
                        "알림 템플릿을 찾을 수 없습니다: id=" + id));
    }

    /** ${key} 패턴을 sampleVariables 값으로 단순 치환한다. */
    private String substitute(String template, Map<String, String> vars) {
        if (template == null) {
            return null;
        }
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }
}
