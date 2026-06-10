package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.dto.CommentAdminListRequest;
import kr.co.ircp.cms.domain.board.dto.CommentAdminSummary;
import kr.co.ircp.cms.domain.board.entity.BbsComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 댓글 MyBatis 매퍼.
 * REQ-BOARD-003: 댓글 CRUD (1-depth 계층형)
 * SPEC-CMS-COMMENT-MODERATE-001: 관리자 모더레이션 쿼리 추가
 */
@Mapper
public interface BbsCommentMapper {

    /** 게시글 댓글 목록 조회 (depth 순 정렬) */
    List<BbsComment> findByPostId(@Param("postId") Long postId);

    /** ID로 단건 조회 */
    Optional<BbsComment> findById(@Param("id") Long id);

    /** 댓글 삽입 */
    void insert(BbsComment comment);

    /** 댓글 수정 */
    int update(BbsComment comment);

    /** 댓글 삭제 (소프트 삭제: status=DELETED) */
    int deleteById(@Param("id") Long id);

    // ── 관리자 모더레이션 (SPEC-CMS-COMMENT-MODERATE-001) ──────────────────────

    /** 관리자용 전체 댓글 목록 조회 (게시판/상태/키워드 필터 + 페이징). REQ-CMTM-001/002 */
    List<CommentAdminSummary> listForAdmin(
            @Param("req") CommentAdminListRequest request,
            @Param("offset") int offset,
            @Param("size") int size);

    /** 관리자용 전체 댓글 카운트 (페이징 계산용). REQ-CMTM-001 */
    long countForAdmin(@Param("req") CommentAdminListRequest request);

    /** 댓글 status 단건 조회 (상태 변경 전 검증용). REQ-CMTM-003/004 */
    Optional<String> findStatusById(@Param("id") Long id);

    /** 관리자용 댓글 단건 DTO 조회 (상태 변경 후 응답용). REQ-CMTM-003 */
    Optional<CommentAdminSummary> findAdminSummaryById(@Param("id") Long id);

    /** 댓글 상태 변경 (VISIBLE/HIDDEN). REQ-CMTM-003 */
    void updateCommentStatus(@Param("id") Long id, @Param("status") String status);

    /** 관리자 소프트 삭제 (status=DELETED, deleted_at=NOW()). REQ-CMTM-004 */
    void adminSoftDelete(@Param("id") Long id);
}
