package kr.co.ircp.cms.domain.content.block.mapper;

import kr.co.ircp.cms.domain.content.block.entity.SharedContentBlock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 공유 콘텐츠 블록 MyBatis 매퍼.
 *
 * <p>SPEC-CMS-CONTENT-BLOCK-001 — shared_content_block CRUD + slug 유일성 검사.
 */
@Mapper
public interface SharedContentBlockMapper {

    void insert(SharedContentBlock block);

    Optional<SharedContentBlock> findById(Long id);

    /** REQ-CB-003/007/013 — updated_at DESC 정렬, status/blockType 선택 필터. */
    List<SharedContentBlock> findAll(@Param("status") String status,
                                     @Param("blockType") String blockType);

    void update(SharedContentBlock block);

    void updateStatus(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("updatedAt") Instant updatedAt);

    void deleteById(Long id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(@Param("slug") String slug, @Param("id") Long id);
}
