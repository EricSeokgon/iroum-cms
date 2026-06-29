package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.PostTranslationRequest;
import kr.co.ircp.cms.domain.board.dto.PostTranslationResponse;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.entity.BbsPostI18n;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostI18nMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.BbsViewLogMapper;
import kr.co.ircp.cms.domain.board.util.AuthorizationGuard;
import kr.co.ircp.cms.domain.board.util.HtmlSanitizer;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostService 다국어 번역 GREEN 단계 테스트.
 * SPEC-CMS-NOTICE-I18N-001: 번역 upsert / 조회 / 삭제.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostService 번역 테스트 (SPEC-CMS-NOTICE-I18N-001)")
class PostTranslationServiceTest {

    @Mock private BbsMasterMapper bbsMasterMapper;
    @Mock private BbsPostMapper bbsPostMapper;
    @Mock private BbsPostHistoryMapper bbsPostHistoryMapper;
    @Mock private BbsViewLogMapper bbsViewLogMapper;
    @Mock private BbsPostI18nMapper bbsPostI18nMapper;
    @Mock private AuthorizationGuard authorizationGuard;

    private final HtmlSanitizer htmlSanitizer = new HtmlSanitizer();

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(
                bbsMasterMapper, bbsPostMapper, bbsPostHistoryMapper,
                bbsViewLogMapper, bbsPostI18nMapper, htmlSanitizer, authorizationGuard,
                org.mockito.Mockito.mock(kr.co.ircp.cms.common.service.RevisionRetentionService.class)
        );
    }

    private BbsPost stubPost(long id) {
        return BbsPost.builder()
                .id(id)
                .bbsId(1L)
                .title("원본 공지 " + id)
                .contentHtml("<p>원본 내용</p>")
                .contentText("원본 내용")
                .status("PUBLISHED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("번역 upsert: 게시글이 존재하면 저장 후 응답 반환")
    void upsertTranslation_persistsAndReturns() {
        long postId = 10L;
        when(bbsPostMapper.findById(postId)).thenReturn(Optional.of(stubPost(postId)));
        // upsert 후 재조회 결과 스텁 (updated_at 포함)
        BbsPostI18n saved = BbsPostI18n.builder()
                .id(100L).postId(postId).language("en")
                .title("Notice").contentHtml("<p>Body</p>").contentText("Body")
                .updatedAt(Instant.now())
                .build();
        when(bbsPostI18nMapper.findByPostIdAndLang(postId, "en")).thenReturn(Optional.of(saved));

        PostTranslationRequest req = new PostTranslationRequest(
                "en", "Notice", "<p>Body</p>", null
        );

        PostTranslationResponse res = postService.upsertTranslation(postId, req);

        assertThat(res.postId()).isEqualTo(postId);
        assertThat(res.language()).isEqualTo("en");
        assertThat(res.title()).isEqualTo("Notice");

        // upsert에 전달된 엔티티 검증 (contentText는 contentHtml에서 파생)
        ArgumentCaptor<BbsPostI18n> captor = ArgumentCaptor.forClass(BbsPostI18n.class);
        verify(bbsPostI18nMapper).upsert(captor.capture());
        BbsPostI18n passed = captor.getValue();
        assertThat(passed.getLanguage()).isEqualTo("en");
        assertThat(passed.getContentText()).isEqualTo("Body");
    }

    @Test
    @DisplayName("번역 upsert: 게시글이 없으면 PostNotFoundException")
    void upsertTranslation_postNotFound() {
        long postId = 999L;
        when(bbsPostMapper.findById(postId)).thenReturn(Optional.empty());

        PostTranslationRequest req = new PostTranslationRequest(
                "en", "Notice", "<p>Body</p>", null
        );

        assertThatThrownBy(() -> postService.upsertTranslation(postId, req))
                .isInstanceOf(PostNotFoundException.class);
        verify(bbsPostI18nMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("번역 조회: 존재하면 응답, 없으면 empty 폴백")
    void getTranslation_foundAndNotFound() {
        long postId = 10L;
        BbsPostI18n entity = BbsPostI18n.builder()
                .id(1L).postId(postId).language("en")
                .title("Notice").contentHtml("<p>Body</p>").contentText("Body")
                .updatedAt(Instant.now())
                .build();
        when(bbsPostI18nMapper.findByPostIdAndLang(postId, "en")).thenReturn(Optional.of(entity));
        when(bbsPostI18nMapper.findByPostIdAndLang(postId, "ja")).thenReturn(Optional.empty());

        Optional<PostTranslationResponse> found = postService.getTranslation(postId, "en");
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Notice");

        Optional<PostTranslationResponse> missing = postService.getTranslation(postId, "ja");
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("번역 목록: 전체 번역을 응답 리스트로 변환")
    void listTranslations_mapsAll() {
        long postId = 10L;
        when(bbsPostI18nMapper.findByPostId(postId)).thenReturn(List.of(
                BbsPostI18n.builder().id(1L).postId(postId).language("en").title("Notice").build(),
                BbsPostI18n.builder().id(2L).postId(postId).language("ko").title("공지").build()
        ));

        List<PostTranslationResponse> list = postService.listTranslations(postId);

        assertThat(list).hasSize(2);
        assertThat(list).extracting(PostTranslationResponse::language)
                .containsExactly("en", "ko");
    }

    @Test
    @DisplayName("번역 삭제: 매퍼 deleteByPostIdAndLang 호출")
    void deleteTranslation_callsMapper() {
        long postId = 10L;

        postService.deleteTranslation(postId, "en");

        verify(bbsPostI18nMapper).deleteByPostIdAndLang(eq(postId), eq("en"));
    }
}
