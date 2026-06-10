package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.PublicationCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 발간자료 카테고리 MyBatis 매퍼.
 * REQ-BOARD-012-D: 카테고리 트리 조회
 * REQ-PCA-001~004: 어드민 CRUD
 */
@Mapper
public interface PublicationCategoryMapper {

    /** 활성 카테고리 전체 조회 (sort_order, id 정렬). */
    List<PublicationCategory> findAllActive();

    /** 어드민용 전체 조회 (INACTIVE 포함, sort_order, id 정렬). */
    List<PublicationCategory> findAllForAdmin();

    /** ID로 단건 조회. */
    Optional<PublicationCategory> findById(@Param("id") Long id);

    /** code 중복 여부 확인. */
    boolean existsByCode(@Param("code") String code);

    /** 카테고리 INSERT (depth 는 DB 트리거가 자동 계산). */
    void insert(PublicationCategory category);

    /** 카테고리 UPDATE (name, sortOrder, status). */
    void update(PublicationCategory category);

    /** 카테고리 DELETE. */
    void deleteById(@Param("id") Long id);

    /** 자식 카테고리 수. */
    long countChildren(@Param("parentId") Long parentId);

    /** 연결된 발간자료 수 (bbs_post_publication_meta). */
    long countLinkedPublications(@Param("categoryId") Long categoryId);
}
