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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContentBlockService 단위 테스트.
 * REQ-CONTENT-006-D: 블록 CRUD + 정렬 + 권한 검증 + KWCAG 1.1.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContentBlockService 테스트 (REQ-CONTENT-006-D)")
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
    // listBlocks()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("listBlocks() — pageId의 블록 목록 반환")
    void listBlocks_returnsAll() {
        when(contentBlockMapper.findByPageId(1L)).thenReturn(List.of(
                stubBlock(1L, 1L, "RICH_TEXT", "{}"),
                stubBlock(2L, 1L, "IMAGE", "{\"url\":\"x\",\"alt\":\"a\"}")));

        List<ContentBlockResponse> result = contentBlockService.listBlocks(1L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("listBlocks() — 빈 결과")
    void listBlocks_empty() {
        when(contentBlockMapper.findByPageId(99L)).thenReturn(List.of());

        List<ContentBlockResponse> result = contentBlockService.listBlocks(99L);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // createBlock()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("RICH_TEXT 블록 생성 시 서버측 sanitize 적용 후 저장")
    void shouldCreateRichTextBlockWithSanitization() {
        String maliciousPayload = "{\"content\":\"<script>alert('XSS')</script><p>내용</p>\"}";
        ContentBlockRequest request = new ContentBlockRequest("RICH_TEXT", 0, maliciousPayload);
        doAnswer(inv -> {
            ContentBlock block = inv.getArgument(0);
            block.setId(1L);
            return null;
        }).when(contentBlockMapper).insert(any(ContentBlock.class));
        Set<String> authorities = Set.of("BLOCK:WRITE");

        ContentBlockResponse response = contentBlockService.createBlock(1L, request, authorities);

        assertThat(response).isNotNull();
        assertThat(response.blockType()).isEqualTo("RICH_TEXT");
        assertThat(response.payload()).doesNotContain("<script>");
    }

    @Test
    @DisplayName("BLOCK:WRITE_HTML 권한 없는 사용자가 HTML 블록 생성 시 PageBlockTypeForbiddenException 발생")
    void shouldRejectHtmlBlockForNonSysadmin() {
        ContentBlockRequest request = new ContentBlockRequest(
                "HTML", 0, "{\"rawHtml\":\"<div>커스텀 HTML</div>\"}");
        Set<String> authorities = Set.of("BLOCK:WRITE");

        assertThatThrownBy(() -> contentBlockService.createBlock(1L, request, authorities))
                .isInstanceOf(PageBlockTypeForbiddenException.class);
        verify(contentBlockMapper, never()).insert(any());
    }

    @Test
    @DisplayName("HTML 블록 + BLOCK:WRITE_HTML 권한 보유 시 정상 INSERT")
    void shouldCreateHtmlBlockWithSysadminAuthority() {
        ContentBlockRequest request = new ContentBlockRequest(
                "HTML", 0, "{\"rawHtml\":\"<div>커스텀</div>\"}");
        Set<String> authorities = Set.of("BLOCK:WRITE_HTML");
        doAnswer(inv -> {
            ContentBlock block = inv.getArgument(0);
            block.setId(99L);
            return null;
        }).when(contentBlockMapper).insert(any(ContentBlock.class));

        ContentBlockResponse response = contentBlockService.createBlock(1L, request, authorities);

        assertThat(response.blockType()).isEqualTo("HTML");
        verify(contentBlockMapper).insert(any());
    }

    @Test
    @DisplayName("EMBED 블록 — sanitize 미적용 (SANITIZE_TYPES 외)")
    void shouldCreateEmbedBlockWithoutSanitize() {
        // EMBED는 sanitize 대상 아님 → payload 그대로 INSERT (Jsoup.clean 호출 안 됨)
        // 단, EMBED는 권한 체크도 없음
        String payload = "{\"src\":\"https://youtube.com/embed/x\"}";
        ContentBlockRequest request = new ContentBlockRequest("EMBED", 0, payload);
        Set<String> authorities = Set.of("BLOCK:WRITE");
        doAnswer(inv -> {
            ContentBlock block = inv.getArgument(0);
            block.setId(1L);
            return null;
        }).when(contentBlockMapper).insert(any(ContentBlock.class));

        ContentBlockResponse response = contentBlockService.createBlock(1L, request, authorities);

        // EMBED은 sanitize 적용되지 않으므로 payload 동일
        assertThat(response.payload()).isEqualTo(payload);
    }

    @Test
    @DisplayName("MARKDOWN 블록 — sanitize 적용 후 저장")
    void shouldCreateMarkdownBlockWithSanitize() {
        String payload = "{\"content\":\"<script>x</script># 제목\"}";
        ContentBlockRequest request = new ContentBlockRequest("MARKDOWN", 0, payload);
        Set<String> authorities = Set.of("BLOCK:WRITE");
        doAnswer(inv -> {
            ContentBlock block = inv.getArgument(0);
            block.setId(1L);
            return null;
        }).when(contentBlockMapper).insert(any(ContentBlock.class));

        ContentBlockResponse response = contentBlockService.createBlock(1L, request, authorities);

        assertThat(response.payload()).doesNotContain("<script>");
    }

    @Test
    @DisplayName("IMAGE 블록 alt 속성 없으면 IllegalArgumentException 발생 (KWCAG 1.1.1)")
    void shouldEnforceImageBlockAltNotNull() {
        String payloadWithoutAlt = "{\"url\":\"https://cdn.example.com/img.png\"}";
        ContentBlockRequest request = new ContentBlockRequest("IMAGE", 0, payloadWithoutAlt);
        Set<String> authorities = Set.of("BLOCK:WRITE");

        assertThatThrownBy(() -> contentBlockService.createBlock(1L, request, authorities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alt");
        verify(contentBlockMapper, never()).insert(any());
    }

    @Test
    @DisplayName("IMAGE 블록 + alt 속성 포함 시 정상 INSERT")
    void shouldCreateImageBlockWithAlt() {
        String payload = "{\"url\":\"https://cdn.example.com/img.png\",\"alt\":\"썸네일\"}";
        ContentBlockRequest request = new ContentBlockRequest("IMAGE", 0, payload);
        Set<String> authorities = Set.of("BLOCK:WRITE");
        doAnswer(inv -> {
            ContentBlock block = inv.getArgument(0);
            block.setId(1L);
            return null;
        }).when(contentBlockMapper).insert(any(ContentBlock.class));

        ContentBlockResponse response = contentBlockService.createBlock(1L, request, authorities);

        assertThat(response.blockType()).isEqualTo("IMAGE");
        verify(contentBlockMapper).insert(any());
    }

    @Test
    @DisplayName("createBlock — INSERT 시 version=1, pageId 자동 설정")
    void createBlock_setsDefaults() {
        ContentBlockRequest request = new ContentBlockRequest("RICH_TEXT", 5, "{\"content\":\"x\"}");
        Set<String> authorities = Set.of("BLOCK:WRITE");
        doAnswer(inv -> {
            ContentBlock block = inv.getArgument(0);
            block.setId(1L);
            return null;
        }).when(contentBlockMapper).insert(any(ContentBlock.class));

        contentBlockService.createBlock(7L, request, authorities);

        ArgumentCaptor<ContentBlock> captor = ArgumentCaptor.forClass(ContentBlock.class);
        verify(contentBlockMapper).insert(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        assertThat(captor.getValue().getPageId()).isEqualTo(7L);
        assertThat(captor.getValue().getSortOrder()).isEqualTo(5);
    }

    // ──────────────────────────────────────────────
    // updateBlock()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("updateBlock — 블록 미존재 시 IllegalArgumentException")
    void updateBlock_throws_when_not_found() {
        when(contentBlockMapper.findById(99L)).thenReturn(Optional.empty());
        ContentBlockRequest request = new ContentBlockRequest("RICH_TEXT", 0, "{\"content\":\"x\"}");

        assertThatThrownBy(() -> contentBlockService.updateBlock(1L, 99L, request, Set.of("BLOCK:WRITE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateBlock — HTML 블록 권한 없으면 PageBlockTypeForbiddenException")
    void updateBlock_htmlWithoutAuthority_throws() {
        ContentBlockRequest request = new ContentBlockRequest("HTML", 0, "{\"rawHtml\":\"<div></div>\"}");

        assertThatThrownBy(() -> contentBlockService.updateBlock(1L, 1L, request, Set.of("BLOCK:WRITE")))
                .isInstanceOf(PageBlockTypeForbiddenException.class);
        verify(contentBlockMapper, never()).update(any());
    }

    @Test
    @DisplayName("updateBlock — IMAGE 블록 alt 누락 시 IllegalArgumentException")
    void updateBlock_imageWithoutAlt_throws() {
        ContentBlockRequest request = new ContentBlockRequest(
                "IMAGE", 0, "{\"url\":\"x\"}");

        assertThatThrownBy(() -> contentBlockService.updateBlock(1L, 1L, request, Set.of("BLOCK:WRITE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateBlock — 정상 흐름에서 version 증가 + update 호출")
    void updateBlock_happyPath_incrementsVersion() {
        ContentBlock existing = stubBlock(1L, 1L, "RICH_TEXT", "{\"content\":\"old\"}");
        existing.setVersion(3);
        when(contentBlockMapper.findById(1L)).thenReturn(Optional.of(existing));

        ContentBlockRequest request = new ContentBlockRequest("RICH_TEXT", 9, "{\"content\":\"new\"}");
        ContentBlockResponse result = contentBlockService.updateBlock(1L, 1L, request, Set.of("BLOCK:WRITE"));

        verify(contentBlockMapper).update(any(ContentBlock.class));
        assertThat(result.version()).isEqualTo(4);
        assertThat(result.sortOrder()).isEqualTo(9);
    }

    // ──────────────────────────────────────────────
    // deleteBlock()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("deleteBlock — mapper.deleteById 호출")
    void deleteBlock_callsMapper() {
        contentBlockService.deleteBlock(1L, 5L);

        verify(contentBlockMapper).deleteById(5L);
    }

    // ──────────────────────────────────────────────
    // reorderBlocks()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("블록 순서 일괄 갱신 — 전체 items 갱신 후 완료")
    void shouldReorderBlocksTransactionally() {
        BlockOrderRequest request = new BlockOrderRequest(List.of(
                new BlockOrderRequest.BlockOrderItem(1L, 0),
                new BlockOrderRequest.BlockOrderItem(2L, 1),
                new BlockOrderRequest.BlockOrderItem(3L, 2)
        ));
        when(contentBlockMapper.updateSortOrder(anyLong(), anyInt())).thenReturn(1);

        contentBlockService.reorderBlocks(1L, request);

        verify(contentBlockMapper).updateSortOrder(1L, 0);
        verify(contentBlockMapper).updateSortOrder(2L, 1);
        verify(contentBlockMapper).updateSortOrder(3L, 2);
    }

    @Test
    @DisplayName("reorderBlocks — 빈 items도 정상 처리 (예외 없음)")
    void reorderBlocks_emptyItems() {
        BlockOrderRequest request = new BlockOrderRequest(List.of());

        // when - 예외 없이 완료
        contentBlockService.reorderBlocks(1L, request);

        // then - mapper 호출 없음
        verify(contentBlockMapper, never()).updateSortOrder(anyLong(), anyInt());
    }
}
