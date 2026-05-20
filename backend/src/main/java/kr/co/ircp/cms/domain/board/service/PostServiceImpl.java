package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.AttachmentSummary;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.dto.PostSummary;
import kr.co.ircp.cms.domain.board.dto.PostUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.entity.BbsPostHistory;
import kr.co.ircp.cms.domain.board.entity.BbsViewLog;
import kr.co.ircp.cms.domain.board.exception.BbsMasterNotFoundException;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.BbsViewLogMapper;
import kr.co.ircp.cms.domain.board.util.AuthorizationGuard;
import kr.co.ircp.cms.domain.board.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 게시글 서비스 구현체.
 * REQ-BOARD-002: 게시글 CRUD + 페이징·검색 + 조회수 dedupe
 *
 * // @MX:NOTE: [AUTO] GREEN 단계 구현 완료. view_log dedupe 후 view_count 증가.
 * // @MX:SPEC: REQ-BOARD-002
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostServiceImpl implements PostService {

    private final BbsMasterMapper bbsMasterMapper;
    private final BbsPostMapper bbsPostMapper;
    private final BbsPostHistoryMapper bbsPostHistoryMapper;
    private final BbsViewLogMapper bbsViewLogMapper;
    private final HtmlSanitizer htmlSanitizer;
    private final AuthorizationGuard authorizationGuard;

    @Override
    public PageResponse<PostSummary> listPosts(Long bbsMasterId, int page, int size) {
        bbsMasterMapper.findById(bbsMasterId)
                .orElseThrow(() -> new BbsMasterNotFoundException(bbsMasterId));
        int offset = page * size;
        List<BbsPost> posts = bbsPostMapper.findByBbsMasterIdPaged(bbsMasterId, offset, size);
        long total = bbsPostMapper.countByBbsMasterId(bbsMasterId);
        List<PostSummary> content = posts.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public PageResponse<PostSummary> searchPosts(Long bbsMasterId, String keyword, int page, int size) {
        bbsMasterMapper.findById(bbsMasterId)
                .orElseThrow(() -> new BbsMasterNotFoundException(bbsMasterId));
        int offset = page * size;
        List<BbsPost> posts = bbsPostMapper.searchByKeywordPaged(bbsMasterId, keyword, offset, size);
        long total = bbsPostMapper.countSearchByKeyword(bbsMasterId, keyword);
        List<PostSummary> content = posts.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public PostDetail getPost(Long id, Long userId, String ipHash) {
        BbsPost post = bbsPostMapper.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));

        // view_log dedupe: 1시간 이내 동일 사용자/IP 중복 조회 방지
        boolean alreadyViewed = bbsViewLogMapper.existsRecentView(id, userId, ipHash);
        if (!alreadyViewed) {
            bbsPostMapper.incrementViewCount(id);
            BbsViewLog viewLog = BbsViewLog.builder()
                    .postId(id)
                    .userId(userId)
                    .ipHash(ipHash != null ? ipHash : "")
                    .userAgentHash("")
                    .build();
            bbsViewLogMapper.insert(viewLog);
        }

        BbsMaster master = bbsMasterMapper.findById(post.getBbsId()).orElse(null);
        String masterCode = master != null ? master.getCode() : null;
        boolean useComment = master != null && master.isUseComment();

        return new PostDetail(
                post.getId(), post.getBbsId(), masterCode, useComment,
                post.getTitle(), post.getContentHtml(),
                post.getAuthorId(), post.getAuthorName(),
                post.isNotice(), post.getNoticeFrom(), post.getNoticeUntil(),
                post.isSecret(), post.getViewCount(), post.getCommentCount(),
                post.getStatus(), null,
                Collections.emptyList(),
                post.getCreatedAt(), post.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public PostDetail createPost(PostCreateRequest request, Long authorId) {
        Long bbsMasterId = request.bbsMasterId();
        BbsMaster master = bbsMasterMapper.findById(bbsMasterId)
                .orElseThrow(() -> new BbsMasterNotFoundException(bbsMasterId));

        // SPEC-CMS-SECURITY-XSS — RICH_TEXT 콘텐츠 Jsoup sanitize 적용
        String sanitizedHtml = htmlSanitizer.sanitize(request.contentHtml());
        BbsPost post = BbsPost.builder()
                .bbsId(bbsMasterId)
                .title(request.title())
                .contentHtml(sanitizedHtml)
                .contentText(request.contentText() != null ? request.contentText() : stripHtml(sanitizedHtml))
                .authorId(authorId)
                .notice(request.isNotice())
                .noticeFrom(request.noticeFrom())
                .noticeUntil(request.noticeUntil())
                .secret(request.isSecret())
                .status("PUBLISHED")
                .build();
        bbsPostMapper.insert(post);

        return new PostDetail(
                post.getId(), post.getBbsId(), master.getCode(), master.isUseComment(),
                post.getTitle(), post.getContentHtml(),
                post.getAuthorId(), post.getAuthorName(),
                post.isNotice(), post.getNoticeFrom(), post.getNoticeUntil(),
                post.isSecret(), post.getViewCount(), post.getCommentCount(),
                post.getStatus(), null,
                Collections.emptyList(),
                post.getCreatedAt(), post.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public PostDetail updatePost(Long id, PostUpdateRequest request, Long editorId) {
        BbsPost existing = bbsPostMapper.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));

        // SPEC-CMS-SECURITY-IDOR — 소유권 검증: 작성자 본인 또는 관리자만 수정 허용
        authorizationGuard.ensureOwnerOrAdmin(existing.getAuthorId(), editorId, "게시글 수정 권한이 없습니다.");

        // 수정 이력 보존
        int nextVersion = bbsPostHistoryMapper.nextVersionByPostId(id);
        BbsPostHistory history = BbsPostHistory.builder()
                .postId(id)
                .version(nextVersion)
                .title(existing.getTitle())
                .contentHtml(existing.getContentHtml())
                .editedBy(editorId)
                .editReason(request != null ? request.editReason() : null)
                .build();
        bbsPostHistoryMapper.insert(history);

        if (request != null) {
            // SPEC-CMS-SECURITY-XSS — RICH_TEXT 콘텐츠 Jsoup sanitize 적용
            String sanitizedHtml = htmlSanitizer.sanitize(request.contentHtml());
            existing.setTitle(request.title());
            existing.setContentHtml(sanitizedHtml);
            existing.setContentText(request.contentText() != null
                    ? request.contentText() : stripHtml(sanitizedHtml));
            existing.setNotice(request.isNotice());
            existing.setNoticeFrom(request.noticeFrom());
            existing.setNoticeUntil(request.noticeUntil());
            existing.setSecret(request.isSecret());
        }
        bbsPostMapper.update(existing);

        BbsMaster master = bbsMasterMapper.findById(existing.getBbsId()).orElse(null);
        String masterCode = master != null ? master.getCode() : null;
        boolean useComment = master != null && master.isUseComment();

        return new PostDetail(
                existing.getId(), existing.getBbsId(), masterCode, useComment,
                existing.getTitle(), existing.getContentHtml(),
                existing.getAuthorId(), existing.getAuthorName(),
                existing.isNotice(), existing.getNoticeFrom(), existing.getNoticeUntil(),
                existing.isSecret(), existing.getViewCount(), existing.getCommentCount(),
                existing.getStatus(), null,
                Collections.emptyList(),
                existing.getCreatedAt(), existing.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public void deletePost(Long id, Long requesterId) {
        BbsPost existing = bbsPostMapper.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
        // SPEC-CMS-SECURITY-IDOR — 소유권 검증: 작성자 본인 또는 관리자만 삭제 허용
        authorizationGuard.ensureOwnerOrAdmin(existing.getAuthorId(), requesterId, "게시글 삭제 권한이 없습니다.");
        bbsPostMapper.deleteById(id);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private PostSummary toSummary(BbsPost p) {
        return new PostSummary(
                p.getId(), p.getBbsId(), null,
                p.getTitle(), p.getAuthorId(), p.getAuthorName(),
                p.isNotice(), p.isSecret(),
                p.getViewCount(), p.getCommentCount(), p.getAttachmentCount(),
                p.getCreatedAt()
        );
    }

    /** HTML 태그를 제거하여 plaintext 추출 (간단 정규식). */
    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }
}
