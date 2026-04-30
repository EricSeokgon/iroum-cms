package kr.co.ircp.cms.domain.content.seo.service;

import kr.co.ircp.cms.domain.content.seo.dto.SeoRedirectRequest;
import kr.co.ircp.cms.domain.content.seo.dto.SeoRedirectResponse;
import kr.co.ircp.cms.domain.content.seo.entity.SeoRedirect;
import kr.co.ircp.cms.domain.content.seo.mapper.SeoRedirectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SeoRedirectService RED→GREEN 테스트.
 * REQ-CONTENT-005-D-8: URL 리다이렉트 CRUD
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SeoRedirectService 테스트 (REQ-CONTENT-005-D-8)")
class SeoRedirectServiceTest {

    @Mock private SeoRedirectMapper seoRedirectMapper;

    private SeoRedirectService seoRedirectService;

    @BeforeEach
    void setUp() {
        seoRedirectService = new SeoRedirectServiceImpl(seoRedirectMapper);
    }

    private SeoRedirect stubRedirect(Long id, String fromPath, String toPath, short status, boolean active) {
        return SeoRedirect.builder()
                .id(id)
                .fromPath(fromPath)
                .toPath(toPath)
                .httpStatus(status)
                .active(active)
                .reason("테스트")
                .createdAt(Instant.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-005-D-8: 301 기본값
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("httpStatus null 이면 301로 기본 설정되어 생성")
    void shouldCreateRedirectWith301Default() {
        // Arrange — httpStatus null 요청
        SeoRedirectRequest request = new SeoRedirectRequest("/old-path", "/new-path", null, "슬러그 변경");

        doAnswer(inv -> {
            SeoRedirect r = inv.getArgument(0);
            // upsert 호출 후 id를 직접 설정하지 않으므로 id는 null — 상태 코드만 검증
            return null;
        }).when(seoRedirectMapper).upsert(any(SeoRedirect.class));

        // Act
        SeoRedirectResponse response = seoRedirectService.createRedirect(request);

        // Assert — 301이 기본값
        assertThat(response.httpStatus()).isEqualTo((short) 301);
        verify(seoRedirectMapper).upsert(any(SeoRedirect.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-005-D-8: chk_redirect_status — 301/302만 허용
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("301/302 외 상태 코드 입력 시 IllegalArgumentException 발생 (chk_redirect_status)")
    void shouldRejectInvalidHttpStatus() {
        // Arrange
        SeoRedirectRequest request = new SeoRedirectRequest("/old-path", "/new-path", (short) 307, "잘못된 상태");

        // Act & Assert
        assertThatThrownBy(() -> seoRedirectService.createRedirect(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("301/302");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-005-D-8: is_active=true 필터
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("is_active=true 인 리다이렉트만 조회 (findActiveByFromPath)")
    void shouldGetActiveRedirectByFromPath() {
        // Arrange
        SeoRedirect active = stubRedirect(1L, "/old-page", "/new-page", (short) 301, true);
        when(seoRedirectMapper.findActiveByFromPath("/old-page")).thenReturn(Optional.of(active));

        // Act
        Optional<SeoRedirectResponse> result = seoRedirectService.getActiveRedirectByFromPath("/old-page");

        // Assert — active=true 인 리다이렉트 반환
        assertThat(result).isPresent();
        assertThat(result.get().fromPath()).isEqualTo("/old-page");
        assertThat(result.get().active()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-005-D-8: 비활성화
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deactivateRedirect 호출 시 mapper.deactivate 실행, 없으면 예외")
    void shouldDeactivateRedirect() {
        // Arrange — 존재하는 경우
        when(seoRedirectMapper.deactivate(1L)).thenReturn(1);

        // Act
        seoRedirectService.deactivateRedirect(1L);

        // Assert
        verify(seoRedirectMapper).deactivate(1L);
    }

    @Test
    @DisplayName("존재하지 않는 id 비활성화 시 IllegalArgumentException 발생")
    void shouldThrowWhenDeactivateNotFound() {
        // Arrange
        when(seoRedirectMapper.deactivate(99L)).thenReturn(0);

        // Act & Assert
        assertThatThrownBy(() -> seoRedirectService.deactivateRedirect(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }
}
