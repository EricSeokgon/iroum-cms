package kr.co.ircp.cms.domain.media.mapper;

import kr.co.ircp.cms.domain.media.dto.MediaSearchRequest;
import kr.co.ircp.cms.domain.media.entity.MediaAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 미디어 자산 MyBatis 매퍼.
 * REQ-MEDIA-001-D ~ REQ-MEDIA-004-D
 */
@Mapper
public interface MediaAssetMapper {

    void insert(MediaAsset asset);

    Optional<MediaAsset> findById(Long id);

    Optional<MediaAsset> findByUuid(UUID uuid);

    List<MediaAsset> search(MediaSearchRequest req);

    long countSearch(MediaSearchRequest req);

    void update(MediaAsset asset);

    void softDelete(@Param("id") Long id);

    /** 활성 사용처 수 조회 (삭제 전 차단 여부 확인) */
    int countActiveUsages(@Param("assetId") Long assetId);

    /** 고아 자산 목록 (활성 사용처 0 + created_at 경과) */
    List<MediaAsset> findOrphans(@Param("olderThanDays") int olderThanDays,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    long countOrphans(@Param("olderThanDays") int olderThanDays);
}
