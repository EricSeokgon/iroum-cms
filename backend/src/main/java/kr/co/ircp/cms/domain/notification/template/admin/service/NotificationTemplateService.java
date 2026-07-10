package kr.co.ircp.cms.domain.notification.template.admin.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateCreateRequest;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplatePreviewResult;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateResponse;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateUpdateRequest;

import java.util.Map;

/**
 * 알림 템플릿 관리 서비스.
 *
 * <p>SPEC-CMS-NOTI-EXT-001.
 */
public interface NotificationTemplateService {

    NotificationTemplateResponse create(NotificationTemplateCreateRequest request, Long actorUserId);

    PageResponse<NotificationTemplateResponse> getAll(Boolean isActive, int page, int size);

    NotificationTemplateResponse getById(Long id);

    NotificationTemplateResponse update(Long id, NotificationTemplateUpdateRequest request, Long actorUserId);

    void delete(Long id);

    NotificationTemplatePreviewResult previewTemplate(Long id, Map<String, String> sampleVariables);
}
