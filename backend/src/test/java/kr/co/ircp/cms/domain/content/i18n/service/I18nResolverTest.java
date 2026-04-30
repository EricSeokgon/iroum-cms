package kr.co.ircp.cms.domain.content.i18n.service;

import kr.co.ircp.cms.domain.content.i18n.dto.I18nResourceItem;
import kr.co.ircp.cms.domain.content.i18n.dto.I18nResponse;
import kr.co.ircp.cms.domain.content.i18n.entity.I18nResource;
import kr.co.ircp.cms.domain.content.i18n.mapper.I18nResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I18nResolver RED→GREEN 테스트.
 * REQ-CONTENT-010-D: 다국어 리소스 + 폴백 체인
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("I18nResolver 테스트 (REQ-CONTENT-010-D)")
class I18nResolverTest {

    @Mock private I18nResourceMapper i18nResourceMapper;

    private I18nResolver i18nResolver;

    @BeforeEach
    void setUp() {
        i18nResolver = new I18nResolverImpl(i18nResourceMapper);
    }

    private I18nResource stubResource(String language, String fieldName, String value) {
        return I18nResource.builder()
                .namespace("page")
                .resourceId(1L)
                .language(language)
                .fieldName(fieldName)
                .value(value)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-010-D-2: 요청 언어로 조회
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("요청 언어 en 으로 필드 조회 시 en 값 반환")
    void shouldResolveFieldsInRequestedLanguage() {
        // Arrange
        when(i18nResourceMapper.findByNamespaceAndResourceIdAndLanguage("page", 1L, "en"))
                .thenReturn(List.of(stubResource("en", "title", "English Title")));
        when(i18nResourceMapper.findByNamespaceAndResourceIdAndLanguage("page", 1L, "ko"))
                .thenReturn(List.of(stubResource("ko", "title", "한국어 제목")));

        // Act
        I18nResponse response = i18nResolver.resolveFields("page", 1L, "en");

        // Assert
        assertThat(response.fields()).containsEntry("title", "English Title");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-010-D-2: 폴백 — default_language로 폴백
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("en 요청 시 해당 필드 없으면 ko(기본 언어)로 폴백")
    void shouldFallbackToDefaultLanguageWhenFieldMissing() {
        // Arrange — en에는 title만, ko에는 title + description
        when(i18nResourceMapper.findByNamespaceAndResourceIdAndLanguage("page", 1L, "en"))
                .thenReturn(List.of(stubResource("en", "title", "English Title")));
        when(i18nResourceMapper.findByNamespaceAndResourceIdAndLanguage("page", 1L, "ko"))
                .thenReturn(List.of(
                        stubResource("ko", "title", "한국어 제목"),
                        stubResource("ko", "description", "한국어 설명")
                ));

        // Act
        I18nResponse response = i18nResolver.resolveFields("page", 1L, "en");

        // Assert — title은 en, description은 ko에서 폴백
        assertThat(response.fields()).containsEntry("title", "English Title");
        assertThat(response.fields()).containsEntry("description", "한국어 설명");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-010-D-2: 폴백 — ko 폴백
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ko 요청 시 ko 리소스 그대로 반환 (기본 언어가 ko이므로 폴백 없음)")
    void shouldFallbackToKoWhenDefaultLanguageMissing() {
        // Arrange
        when(i18nResourceMapper.findByNamespaceAndResourceIdAndLanguage("page", 1L, "ko"))
                .thenReturn(List.of(stubResource("ko", "title", "한국어 제목")));

        // Act
        I18nResponse response = i18nResolver.resolveFields("page", 1L, "ko");

        // Assert
        assertThat(response.fields()).containsEntry("title", "한국어 제목");
        assertThat(response.language()).isEqualTo("ko");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-010-D: bulk upsert
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bulkUpsert 호출 시 mapper.upsertBatch 실행 (ON CONFLICT UPDATE)")
    void shouldBulkUpsertI18nResources() {
        // Arrange
        List<I18nResourceItem> items = List.of(
                new I18nResourceItem("page", 1L, "ko", "title", "제목"),
                new I18nResourceItem("page", 1L, "en", "title", "Title")
        );

        // Act
        i18nResolver.bulkUpsert(items);

        // Assert
        verify(i18nResourceMapper).upsertBatch(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 네임스페이스 검증
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("지원하지 않는 namespace 입력 시 예외 발생 (chk_i18n_namespace)")
    void shouldRejectUnsupportedNamespace() {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> i18nResolver.resolveFields("invalid_ns", 1L, "ko"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 언어 검증
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("지원하지 않는 언어(ko/en 외) 요청 시 예외 발생 (chk_i18n_language)")
    void shouldRejectUnsupportedLanguage() {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> i18nResolver.resolveFields("page", 1L, "fr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("language");
    }
}
