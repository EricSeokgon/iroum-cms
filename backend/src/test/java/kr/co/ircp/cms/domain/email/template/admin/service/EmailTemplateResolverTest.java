package kr.co.ircp.cms.domain.email.template.admin.service;

import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.entity.EmailTemplate;
import kr.co.ircp.cms.domain.email.template.admin.repository.EmailTemplateMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * EmailTemplateResolver 단위 테스트 (T8).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-030/033 — 활성 템플릿 렌더링, 미존재/실패 시 빈 결과.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailTemplateResolver — resolveAndRender (REQ-ET-030/033)")
class EmailTemplateResolverTest {

    @Mock
    EmailTemplateMapper templateMapper;

    @Mock
    EmailTemplateRenderer renderer;

    @InjectMocks
    EmailTemplateResolver resolver;

    @Test
    @DisplayName("활성 템플릿이 있으면 렌더링 결과를 반환한다")
    void returnsRenderedWhenActiveTemplateExists() {
        EmailTemplate template = EmailTemplate.builder()
                .id(1L).code("OTP").language("ko")
                .subject("제목").bodyHtml("<p>본문</p>").isActive(true)
                .build();
        when(templateMapper.findActiveByCodeAndLanguage("OTP", "ko"))
                .thenReturn(Optional.of(template));
        when(renderer.render(any(), any(), any(), anyMap(), any()))
                .thenReturn(new RenderResult("제목", "<p>본문</p>", null));

        Optional<RenderResult> result = resolver.resolveAndRender("OTP", "ko", Map.of());

        assertThat(result).isPresent();
        assertThat(result.get().subject()).isEqualTo("제목");
    }

    @Test
    @DisplayName("AC-ET-011: 활성 템플릿이 없으면 빈 결과(fallback 신호)를 반환한다")
    void returnsEmptyWhenNoActiveTemplate() {
        when(templateMapper.findActiveByCodeAndLanguage(eq("OTP"), anyString()))
                .thenReturn(Optional.empty());

        Optional<RenderResult> result = resolver.resolveAndRender("OTP", "ko", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("AC-ET-011: 렌더링 중 예외가 발생해도 전파하지 않고 빈 결과를 반환한다")
    void swallowsRenderExceptionAndReturnsEmpty() {
        EmailTemplate template = EmailTemplate.builder()
                .id(1L).code("OTP").language("ko")
                .subject("제목").bodyHtml("<p>본문</p>").isActive(true)
                .build();
        when(templateMapper.findActiveByCodeAndLanguage("OTP", "ko"))
                .thenReturn(Optional.of(template));
        when(renderer.render(any(), any(), any(), anyMap(), any()))
                .thenThrow(new RuntimeException("렌더링 폭발"));

        Optional<RenderResult> result = resolver.resolveAndRender("OTP", "ko", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("language가 null이면 ko로 조회한다")
    void defaultsLanguageToKo() {
        when(templateMapper.findActiveByCodeAndLanguage("OTP", "ko"))
                .thenReturn(Optional.empty());

        Optional<RenderResult> result = resolver.resolveAndRender("OTP", null, Map.of());

        assertThat(result).isEmpty();
    }
}
