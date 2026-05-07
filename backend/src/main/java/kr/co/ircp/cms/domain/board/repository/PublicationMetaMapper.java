package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.dto.PublicationCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PublicationUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.PublicationMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 발간자료 메타 MyBatis 매퍼.
 * REQ-BOARD-012: 발간자료 CRUD + 페이징·검색
 */
@Mapper
public interface PublicationMetaMapper {

    /** 필터 기반 발간자료 페이징 조회 (bbs_post + publication_category 조인). */
    List<PublicationMeta> findWithFilters(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("documentType") String documentType,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    /** 필터 기반 카운트. */
    long countWithFilters(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("documentType") String documentType,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword
    );

    /** ID로 단건 조회 (조인 포함). */
    Optional<PublicationMeta> findById(@Param("id") Long id);

    /** bbs_post_publication_meta 신규 INSERT. */
    void insert(@Param("postId") Long postId, @Param("req") PublicationCreateRequest req);

    /** bbs_post_publication_meta 부분 UPDATE. */
    void update(@Param("postId") Long postId, @Param("req") PublicationUpdateRequest req);

    /** 조회수 1 증가 (bbs_post.view_count). */
    void incrementViewCount(@Param("id") Long id);
}
