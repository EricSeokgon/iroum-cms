package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.dto.BlockOrderRequest;
import kr.co.ircp.cms.domain.content.page.dto.ContentBlockRequest;
import kr.co.ircp.cms.domain.content.page.dto.ContentBlockResponse;
import kr.co.ircp.cms.domain.content.page.entity.ContentBlock;
import kr.co.ircp.cms.domain.content.page.exception.PageBlockTypeForbiddenException;
import kr.co.ircp.cms.domain.content.page.mapper.ContentBlockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContentBlockService RED 단계 테스트.
 * REQ-CONTENT-006-D: 블록 CRUD + 정렬 + 권한 검증 + KWCAG 1.1.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContentBlockService RED 테스트 (REQ-CONTENT-006-D)")
class ContentBlockServiceTest {

    @Mock
    private ContentBlockMapper contentBlockMapper;

    private ContentBlockService contentBlockService;

    @BeforeEach
    void setUp() {
        contentBlockService = new ContentBlockServiceImpl(contentBlockMapper);
    }

    private ContentBlock stubBlock(long id, long pageId, String blockType, String payload) {
        return ContentBlock.builder()
                .id(id)
                .pageId(pageId)
                .blockType(blockType)
                .sortOrder(0)
                .payload(payload)
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-006-D-1: RICH_TEXT 블록 생성 + sanitize
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("RICH_TEXT 블록 생성 시 서버측 sanitize 적용 후 저장")
    void shouldCreateRichTextBlockWithSanitization() {
        // Arrange — XSS 가능 입력 포함 payload
        String maliciousPayload = "{\"content\":\"<script>alert('XSS')</script><p>내용</p>\"}";
        ContentBlockRequest request = new ContentBlockRequest("RICH_TEXT", 0, maliciousPayload);
        doAnswer(inv -> {
            ContentBlock block = inv.getArgument(0);
            block.setId(1L);
            return null;
        }).when(contentBlockMapper).insert(any(ContentBlock.class));
        Set<String> authorities = Set.of("BLOCK:WRITE");

        // Act
        ContentBlockResponse response = contentBlockService.createBlock(1L, request, authorities);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.blockType()).isEqualTo("RICH_TEXT");
        // sanitize 후 <script> 태그 제거 여부 검증
        assertThat(response.payload()).doesNotContain("<script>");
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-006-D-1: HTML 블록 권한 검증 (SYSADMIN 전용)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("BLOCK:WRITE_HTML 권한 없는 사용자가 HTML 블록 생성 시 PageBlockTypeForbiddenException 발생")
    void shouldRejectHtmlBlockForNonSysadmin() {
        // Arrange
        ContentBlockRequest request = new ContentBlockRequest(
                "HTML", 0, "{\"rawHtml\":\"<div>커스텀 HTML</div>\"}"
        );
        // BLOCK:WRITE_HTML 권한 없음
        Set<String> authorities = Set.of("BLOCK:WRITE");

        // Act & Assert
        assertThatThrownBy(() -> contentBlockService.createBlock(1L, request, authorities))
                .isInstanceOf(PageBlockTypeForbiddenException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-006-D-2: 블록 순서 일괄 갱신 (트랜잭션)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("블록 순서 일괄 갱신 — 전체 items 갱신 후 완료")
    void shouldReorderBlocksTransactionally() {
        // Arrange
        BlockOrderRequest request = new BlockOrderRequest(List.of(
                new BlockOrderRequest.BlockOrderItem(1L, 0),
                new BlockOrderRequest.BlockOrderItem(2L, 1),
                new BlockOrderRequest.BlockOrderItem(3L, 2)
        ));
        when(contentBlockMapper.updateSortOrder(anyLong(), anyInt())).thenReturn(1);

        // Act — 예외 없이 완료되어야 함
        contentBlockService.reorderBlocks(1L, request);

        // Assert — 각 블록에 대해 updateSortOrder 호출 검증
        verify(contentBlockMapper).updateSortOrder(1L, 0);
        verify(contentBlockMapper).updateSortOrder(2L, 1);
        verify(contentBlockMapper).updateSortOrder(3L, 2);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-006-D-1: IMAGE 블록 alt 필수 (KWCAG 1.1.1)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("IMAGE 블록 alt 속성 없으면 IllegalArgumentException 발생 (KWCAG 1.1.1)")
    void shouldEnforceImageBlockAltNotNull() {
        // Arrange — alt 없는 IMAGE 블록 payload
        String payloadWithoutAlt = "{\"url\":\"https://cdn.example.com/img.png\"}";
        ContentBlockRequest request = new ContentBlockRequest("IMAGE", 0, payloadWithoutAlt);
        Set<String> authorities = Set.of("BLOCK:WRITE");

        // Act & Assert — KWCAG 1.1.1: 이미지 대체 텍스트 의무 (alt 필수)
        assertThatThrownBy(() -> contentBlockService.createBlock(1L, request, authorities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alt");
    }
}
