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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationTemplateService 단위 테스트 (RED → GREEN).
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — CRUD + 중복검출 + 미리보기 치환.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationTemplateService — CRUD + 미리보기 (SPEC-CMS-NOTI-EXT-001)")
class NotificationTemplateServiceImplTest {

    @Mock
    private NotificationTemplateMapper mapper;

    @InjectMocks
    private NotificationTemplateServiceImpl service;

    private NotificationTemplate sample(Long id) {
        return NotificationTemplate.builder()
                .id(id)
                .code("POLICY_OPEN")
                .name("정책 공개 알림")
                .channel("EMAIL")
                .subject("[알림] ${policyName}")
                .bodyHtml("<p>${policyName} 신청이 시작되었습니다.</p>")
                .variables("[\"policyName\"]")
                .language("ko")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("create — 신규 코드 등록 시 응답 반환")
    void create_WhenValidRequest_ReturnsResponse() {
        var req = new NotificationTemplateCreateRequest(
                "POLICY_OPEN", "정책 공개 알림", "EMAIL",
                "[알림] ${policyName}", "<p>${policyName}</p>", "[\"policyName\"]",
                "ko", true, null);
        when(mapper.existsByCodeAndLanguage("POLICY_OPEN", "ko", null)).thenReturn(false);

        NotificationTemplateResponse result = service.create(req, 1L);

        assertThat(result.code()).isEqualTo("POLICY_OPEN");
        verify(mapper).insert(any(NotificationTemplate.class));
    }

    @Test
    @DisplayName("create — 동일 (code, language) 중복 시 DuplicateNotificationTemplateException")
    void create_WhenDuplicate_Throws() {
        var req = new NotificationTemplateCreateRequest(
                "POLICY_OPEN", "정책 공개 알림", "EMAIL",
                "subj", "body", null, "ko", true, null);
        when(mapper.existsByCodeAndLanguage("POLICY_OPEN", "ko", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(req, 1L))
                .isInstanceOf(DuplicateNotificationTemplateException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    @DisplayName("getById — 존재 시 응답 반환")
    void getById_WhenExists_ReturnsResponse() {
        when(mapper.findById(10L)).thenReturn(Optional.of(sample(10L)));

        NotificationTemplateResponse result = service.getById(10L);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.code()).isEqualTo("POLICY_OPEN");
    }

    @Test
    @DisplayName("getById — 미존재 시 NotificationTemplateNotFoundException")
    void getById_WhenMissing_Throws() {
        when(mapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(NotificationTemplateNotFoundException.class);
    }

    @Test
    @DisplayName("getAll — 페이징 응답 반환")
    void getAll_ReturnsPagedResponse() {
        when(mapper.countAll(null)).thenReturn(2L);
        when(mapper.findAll(eq(null), eq(0), eq(20)))
                .thenReturn(List.of(sample(1L), sample(2L)));

        PageResponse<NotificationTemplateResponse> page = service.getAll(null, 0, 20);

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("update — 존재 시 갱신 후 응답 반환")
    void update_WhenExists_ReturnsUpdated() {
        when(mapper.findById(10L)).thenReturn(Optional.of(sample(10L)));
        var req = new NotificationTemplateUpdateRequest(
                "수정된 이름", "INAPP", "수정 제목", "<p>수정</p>", null, false, null);

        NotificationTemplateResponse result = service.update(10L, req, 2L);

        assertThat(result.name()).isEqualTo("수정된 이름");
        verify(mapper).update(any(NotificationTemplate.class));
    }

    @Test
    @DisplayName("delete — 존재 시 삭제 호출")
    void delete_WhenExists_CallsMapper() {
        when(mapper.findById(10L)).thenReturn(Optional.of(sample(10L)));

        service.delete(10L);

        verify(mapper).delete(10L);
    }

    @Test
    @DisplayName("previewTemplate — ${var} 치환 결과 반환")
    void previewTemplate_SubstitutesVariables() {
        when(mapper.findById(10L)).thenReturn(Optional.of(sample(10L)));

        NotificationTemplatePreviewResult result =
                service.previewTemplate(10L, Map.of("policyName", "청년창업지원"));

        assertThat(result.subject()).isEqualTo("[알림] 청년창업지원");
        assertThat(result.bodyHtml()).contains("청년창업지원");
    }
}
