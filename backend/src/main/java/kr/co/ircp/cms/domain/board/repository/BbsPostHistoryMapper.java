package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
import kr.co.ircp.cms.domain.board.dto.PostHistoryItem;
import kr.co.ircp.cms.domain.board.entity.BbsPostHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 변경 이력 MyBatis 매퍼.
 * REQ-BOARD-002-D-4: 수정 직전 본문 보존 (write 경로)
 * SPEC-CMS-POST-HISTORY-001: 버전 히스토리 read 경로 (페이징 목록 + 단건)
 */
@Mapper
public interface BbsPostHistoryMapper {

    /** 게시글 변경 이력 목록 조회 (버전 역순) */
    List<BbsPostHistory> findByPostId(@Param("postId") Long postId);

    /** 이력 삽입 */
    void insert(BbsPostHistory history);

    /** 다음 버전 번호 조회 */
    int nextVersionByPostId(@Param("postId") Long postId);

    // ─── SPEC-CMS-POST-HISTORY-001: read 전용 조회 ──────────────────────────────

    /**
     * 게시글 버전 히스토리 페이징 목록 조회 (version DESC, 본문 제외, editor LEFT JOIN).
     * REQ-PH-001/002/003
     */
    List<PostHistoryItem> findPageByPostId(
            @Param("postId") Long postId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /** 게시글 버전 히스토리 전체 건수. REQ-PH-001 */
    long countByPostId(@Param("postId") Long postId);

    /**
     * 최신 {@code keepCount}개를 제외한 오래된 이력 삭제.
     * SPEC-CMS-CONTENT-REVISION-001 M3 (REQ-REV-006) — 리비전 보존 정책 적용.
     * version DESC 기준 상위 keepCount 행만 남기고 나머지를 삭제한다.
     *
     * @param postId    게시글 ID
     * @param keepCount 보존할 최신 이력 개수
     * @return 삭제된 행 수
     */
    int deleteOldestByPostId(@Param("postId") Long postId, @Param("keepCount") int keepCount);

    /**
     * 특정 (postId, version) 단건 본문 조회 (title + content_html, editor LEFT JOIN).
     * REQ-PH-004/005
     */
    Optional<PostHistoryDetail> findByPostIdAndVersion(
            @Param("postId") Long postId,
            @Param("version") int version
    );
}
