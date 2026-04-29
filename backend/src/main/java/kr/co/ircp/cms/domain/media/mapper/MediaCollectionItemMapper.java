package kr.co.ircp.cms.domain.media.mapper;

import kr.co.ircp.cms.domain.media.entity.MediaCollectionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 컬렉션-자산 매핑 매퍼.
 * REQ-MEDIA-005-D-1, REQ-MEDIA-005-D-3
 */
@Mapper
public interface MediaCollectionItemMapper {

    void insert(MediaCollectionItem item);

    List<MediaCollectionItem> findByCollectionId(@Param("collectionId") Long collectionId);

    void delete(@Param("collectionId") Long collectionId,
                @Param("assetId") Long assetId);

    void deleteByCollectionId(@Param("collectionId") Long collectionId);
}
