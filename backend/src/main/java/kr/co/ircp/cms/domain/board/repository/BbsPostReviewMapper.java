package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.dto.AdminReviewResponse;
import kr.co.ircp.cms.domain.board.dto.ReviewAggregate;
import kr.co.ircp.cms.domain.board.entity.BbsPostReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 게시물 별점 리뷰 MyBatis 매퍼.
 * SPEC-CMS-REVIEW-001 REQ-REV-001~012
 *
 * // @MX:ANCHOR: [AUTO] BbsPostReviewMapper — 리뷰 데이터 접근 핵심 계층
 * // @MX:REASON: ReviewService(공개) + ReviewAdminService(관리) 양쪽에서 참조 (fan_in >= 3)
 * // @MX:SPEC: SPEC-CMS-REVIEW-001
 */
@Mapper
public interface BbsPostReviewMapper {

    /** 리뷰 삽입 (생성 키를 엔티티 id 에 채움). */
    void insert(BbsPostReview review);

    /** ID 로 단건 조회 (DELETED 포함 — 관리 흐름에서 상태 검증용). */
    Optional<BbsPostReview> findById(@Param("id") Long id);

    /** 게시물 공개 리뷰 목록 (VISIBLE 만, created_at DESC). REQ-REV-005 */
    List<BbsPostReview> selectByPostId(@Param("postId") Long postId);

    /**
     * 관리자 리뷰 목록 (페이징). postId / status 선택 필터.
     * status=null 이면 DELETED 제외 전체, status 지정 시 해당 상태만. REQ-REV-004/011
     */
    List<AdminReviewResponse> selectAdminPage(
            @Param("postId") Long postId,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /** 관리자 리뷰 전체 수 (페이징 total). */
    long countAdminPage(
            @Param("postId") Long postId,
            @Param("status") String status
    );

    // @MX:WARN: [AUTO] DELETED 는 비가역 — status 전이만 수행하고 물리 삭제(DELETE) 금지.
    // @MX:REASON: REQ-REV-006 — 삭제된 리뷰의 복구/재활성화를 시스템 차원에서 차단.
    /** 리뷰 상태 변경 (HIDDEN/DELETED). DELETED 시 deletedAt 기록. */
    int updateStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("deletedAt") Instant deletedAt
    );

    /**
     * 게시물의 VISIBLE 리뷰 집계(개수 + 평균). REQ-REV-003
     * 리뷰 0건이면 count=0, average=null.
     */
    ReviewAggregate aggregateVisible(@Param("postId") Long postId);
}
