package kr.co.ircp.cms.domain.media.mapper;

import kr.co.ircp.cms.domain.media.entity.MediaAssetUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 미디어 자산 사용처 매퍼.
 * REQ-MEDIA-003-D-5, REQ-MEDIA-004-D-2
 */
@Mapper
public interface MediaAssetUsageMapper {

    void insert(MediaAssetUsage usage);

    List<MediaAssetUsage> findActiveByAssetId(@Param("assetId") Long assetId);

    /** removed_at 갱신 (사용처 해제) */
    void removeUsage(@Param("assetId") Long assetId,
                     @Param("usedIn") String usedIn,
                     @Param("referenceId") Long referenceId,
                     @Param("referenceTable") String referenceTable);
}
