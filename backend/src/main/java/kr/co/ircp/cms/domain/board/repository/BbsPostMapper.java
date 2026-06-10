package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.BbsPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 MyBatis 매퍼.
 * REQ-BOARD-002: 게시글 CRUD + 페이징·검색
 *
 * // @MX:ANCHOR: [AUTO] BbsPostMapper — 게시글 CRUD·검색 핵심 데이터 접근 계층
 * // @MX:REASON: PostService (fan_in >= 3: CRUD + 검색 + 조회수)
 */
@Mapper
public interface BbsPostMapper {

    /** 게시글 목록 페이징 조회 */
    List<BbsPost> findByBbsMasterIdPaged(
            @Param("bbsMasterId") Long bbsMasterId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /** 전문검색 (TSVECTOR) 페이징 조회 */
    List<BbsPost> searchByKeywordPaged(
            @Param("bbsMasterId") Long bbsMasterId,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /** 게시글 수 조회 */
    long countByBbsMasterId(@Param("bbsMasterId") Long bbsMasterId);

    /** 전문검색 결과 수 조회 */
    long countSearchByKeyword(
            @Param("bbsMasterId") Long bbsMasterId,
            @Param("keyword") String keyword
    );

    /** ID로 단건 조회 */
    Optional<BbsPost> findById(@Param("id") Long id);

    /** 게시글 삽입 */
    void insert(BbsPost post);

    /** 게시글 수정 */
    int update(BbsPost post);

    /** 조회수 1 증가 */
    int incrementViewCount(@Param("id") Long id);

    /** 게시글 삭제 (소프트 삭제: status=DELETED) */
    int deleteById(@Param("id") Long id);

    // ─── SPEC-CMS-POST-SCHEDULE-001: 예약 발행 ──────────────────────────────────

    /** 예약 처리 (status=SCHEDULED, scheduled_at=?) */
    int schedule(@Param("id") Long id, @Param("scheduledAt") java.time.Instant scheduledAt);

    /** 예약 취소 (status=DRAFT, scheduled_at=NULL) */
    int clearSchedule(@Param("id") Long id);

    /** 예약 만기 발행 (status=PUBLISHED, published_at=NOW(), scheduled_at=NULL). 멱등: WHERE status='SCHEDULED' */
    int publishScheduled(@Param("id") Long id);

    /** 만기 예약 게시글 조회 (scheduled_at <= NOW() AND status='SCHEDULED' AND deleted_at IS NULL) */
    List<BbsPost> findScheduledDue();
}
