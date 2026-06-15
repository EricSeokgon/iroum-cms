package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.AttachmentSummary;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.dto.PostSummary;
import kr.co.ircp.cms.domain.board.dto.PostTranslationRequest;
import kr.co.ircp.cms.domain.board.dto.PostTranslationResponse;
import kr.co.ircp.cms.domain.board.dto.PostUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.entity.BbsPostHistory;
import kr.co.ircp.cms.domain.board.entity.BbsPostI18n;
import kr.co.ircp.cms.domain.board.entity.BbsViewLog;
import kr.co.ircp.cms.domain.board.exception.BbsMasterNotFoundException;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.exception.PostScheduleConflictException;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostI18nMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.BbsViewLogMapper;
import kr.co.ircp.cms.domain.board.util.AuthorizationGuard;
import kr.co.ircp.cms.domain.board.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
    private final BbsPostI18nMapper bbsPostI18nMapper;
    private final HtmlSanitizer htmlSanitizer;
    private final AuthorizationGuard authorizationGuard;

    @Override
    public PageResponse<PostSummary> listPosts(Long bbsMasterId, int page, int size, String lang) {
        bbsMasterMapper.findById(bbsMasterId)
                .orElseThrow(() -> new BbsMasterNotFoundException(bbsMasterId));
        int offset = page * size;
        // SPEC-CMS-NOTICE-I18N-002: lang 전달 → LEFT JOIN으로 번역 제목 오버레이.
        List<BbsPost> posts = bbsPostMapper.findByBbsMasterIdPaged(bbsMasterId, offset, size, lang);
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
                post.getTags(),
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
                // SPEC-CMS-AI-004: 요청 태그 반영 (null/미전송 시 빈 목록 — TypeHandler NPE 방지)
                .tags(request.tags() != null ? request.tags() : Collections.emptyList())
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
                post.getTags(),
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
            // SPEC-CMS-AI-004: 요청에 태그가 있으면 교체, null/미전송이면 기존 태그 유지
            if (request.tags() != null) {
                existing.setTags(request.tags());
            }
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
                existing.getTags(),
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

    // ─── SPEC-CMS-POST-SCHEDULE-001: 예약 발행 ──────────────────────────────────

    /**
     * 게시글 예약 발행.
     * REQ-POST-SCHEDULE-001/002/007: scheduledAt > now 검증, DELETED 게시글 거부(409),
     * 미존재 404. Page.schedulePage 로직 차용.
     */
    @Override
    @Transactional
    public PostDetail schedulePost(Long id, kr.co.ircp.cms.domain.board.dto.PostScheduleRequest request) {
        BbsPost post = bbsPostMapper.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));

        // REQ-POST-SCHEDULE-007-2: DELETED 게시글은 예약 불가 (409)
        if ("DELETED".equals(post.getStatus())) {
            throw new PostScheduleConflictException(
                    "삭제된 게시글은 예약할 수 없습니다. id=" + id);
        }

        // REQ-POST-SCHEDULE-002-1: scheduledAt 은 현재 시각 이후여야 함 (400)
        if (!request.scheduledAt().isAfter(java.time.Instant.now())) {
            throw new IllegalArgumentException(
                    "예약 발행 시간은 현재 시각 이후여야 합니다. scheduledAt=" + request.scheduledAt());
        }

        bbsPostMapper.schedule(id, request.scheduledAt());
        // 메모리 상 상태 갱신 후 반환 (DB 재조회 없이)
        post.setStatus("SCHEDULED");
        post.setScheduledAt(request.scheduledAt());
        return toDetail(post);
    }

    /**
     * 게시글 예약 취소.
     * REQ-POST-SCHEDULE-004: SCHEDULED → DRAFT 복귀, scheduled_at=NULL.
     * 비SCHEDULED 게시글 취소는 409.
     */
    @Override
    @Transactional
    public PostDetail cancelSchedule(Long id) {
        BbsPost post = bbsPostMapper.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));

        // REQ-POST-SCHEDULE-004-2: SCHEDULED 가 아니면 취소 불가 (409)
        if (!"SCHEDULED".equals(post.getStatus())) {
            throw new PostScheduleConflictException(
                    "예약 상태(SCHEDULED)인 게시글만 취소할 수 있습니다. id=" + id + ", status=" + post.getStatus());
        }

        bbsPostMapper.clearSchedule(id);
        post.setStatus("DRAFT");
        post.setScheduledAt(null);
        return toDetail(post);
    }

    /** BbsPost → PostDetail 변환 (master 코드/댓글 사용 여부 조회 포함). */
    private PostDetail toDetail(BbsPost post) {
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
                post.getTags(),
                post.getCreatedAt(), post.getUpdatedAt()
        );
    }

    // ─── SPEC-CMS-NOTICE-I18N-001: 다국어 번역 ─────────────────────────────────

    @Override
    @Transactional
    public PostTranslationResponse upsertTranslation(Long postId, PostTranslationRequest req) {
        // 원본 게시글 존재 검증
        bbsPostMapper.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        // SPEC-CMS-SECURITY-XSS — RICH_TEXT 콘텐츠 Jsoup sanitize 적용
        String sanitizedHtml = req.contentHtml() != null
                ? htmlSanitizer.sanitize(req.contentHtml()) : null;
        String contentText = req.contentText() != null
                ? req.contentText() : stripHtml(sanitizedHtml);

        BbsPostI18n entity = BbsPostI18n.builder()
                .postId(postId)
                .language(req.language())
                .title(req.title())
                .contentHtml(sanitizedHtml)
                .contentText(contentText)
                .build();
        bbsPostI18nMapper.upsert(entity);

        // upsert 후 정규 상태(updated_at 등) 재조회하여 반환
        return bbsPostI18nMapper.findByPostIdAndLang(postId, req.language())
                .map(PostTranslationResponse::from)
                .orElse(PostTranslationResponse.from(entity));
    }

    @Override
    public Optional<PostTranslationResponse> getTranslation(Long postId, String language) {
        return bbsPostI18nMapper.findByPostIdAndLang(postId, language)
                .map(PostTranslationResponse::from);
    }

    @Override
    public List<PostTranslationResponse> listTranslations(Long postId) {
        return bbsPostI18nMapper.findByPostId(postId).stream()
                .map(PostTranslationResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTranslation(Long postId, String language) {
        bbsPostI18nMapper.deleteByPostIdAndLang(postId, language);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private PostSummary toSummary(BbsPost p) {
        // SPEC-CMS-NOTICE-I18N-002: language 필드는 mapper LEFT JOIN 결과에서 채워짐.
        // lang='ko'이거나 번역 없을 때 null → 'ko' 기본값 처리.
        String language = p.getLanguage() != null ? p.getLanguage() : "ko";
        return new PostSummary(
                p.getId(), p.getBbsId(), null,
                p.getTitle(), p.getAuthorId(), p.getAuthorName(),
                p.isNotice(), p.isSecret(),
                p.getViewCount(), p.getCommentCount(), p.getAttachmentCount(),
                p.getCreatedAt(), language, p.getTags()
        );
    }

    /** HTML 태그를 제거하여 plaintext 추출 (간단 정규식). */
    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }
}
