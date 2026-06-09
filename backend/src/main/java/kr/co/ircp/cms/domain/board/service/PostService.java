package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.dto.PostScheduleRequest;
import kr.co.ircp.cms.domain.board.dto.PostSummary;
import kr.co.ircp.cms.domain.board.dto.PostTranslationRequest;
import kr.co.ircp.cms.domain.board.dto.PostTranslationResponse;
import kr.co.ircp.cms.domain.board.dto.PostUpdateRequest;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 서비스 인터페이스.
 * REQ-BOARD-002: 게시글 CRUD + 페이징·검색
 *
 * // @MX:ANCHOR: [AUTO] PostService — 게시글 비즈니스 계약
 * // @MX:REASON: PostController, CommentService, AttachmentService에서 참조 (fan_in >= 3)
 * // @MX:SPEC: REQ-BOARD-002
 */
public interface PostService {

    /** 게시글 목록 페이징 조회 */
    PageResponse<PostSummary> listPosts(Long bbsMasterId, int page, int size);

    /** 게시글 전문검색 페이징 조회 */
    PageResponse<PostSummary> searchPosts(Long bbsMasterId, String keyword, int page, int size);

    /**
     * 게시글 단건 상세 조회.
     * 중복 제거(view_log dedupe) 후 조회수 증가.
     *
     * @param userId  로그인 사용자 ID (비로그인: null)
     * @param ipHash  IP 해시 (비로그인 dedupe 식별용)
     */
    PostDetail getPost(Long id, Long userId, String ipHash);

    /** 게시글 작성 */
    PostDetail createPost(PostCreateRequest request, Long authorId);

    /** 게시글 수정 (수정 이력 보존) */
    PostDetail updatePost(Long id, PostUpdateRequest request, Long editorId);

    /** 게시글 삭제 (소프트 삭제) */
    void deletePost(Long id, Long requesterId);

    // ─── SPEC-CMS-POST-SCHEDULE-001: 예약 발행 ──────────────────────────────────

    /**
     * 게시글 예약 발행. scheduledAt > now 검증 후 status=SCHEDULED 로 전환.
     * REQ-POST-SCHEDULE-001 / 002 / 007
     */
    PostDetail schedulePost(Long id, PostScheduleRequest request);

    /**
     * 게시글 예약 취소. SCHEDULED → DRAFT 복귀, scheduled_at=NULL.
     * REQ-POST-SCHEDULE-004
     */
    PostDetail cancelSchedule(Long id);

    // ─── SPEC-CMS-NOTICE-I18N-001: 다국어 번역 ─────────────────────────────────

    /** 번역 등록/수정 (upsert). postId 존재 검증 후 저장. */
    PostTranslationResponse upsertTranslation(Long postId, PostTranslationRequest req);

    /** 단건 번역 조회. 없으면 empty. */
    Optional<PostTranslationResponse> getTranslation(Long postId, String language);

    /** 게시글의 전체 번역 목록 조회. */
    List<PostTranslationResponse> listTranslations(Long postId);

    /** 번역 삭제. */
    void deleteTranslation(Long postId, String language);
}
