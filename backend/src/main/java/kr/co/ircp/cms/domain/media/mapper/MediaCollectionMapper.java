package kr.co.ircp.cms.domain.media.mapper;

import kr.co.ircp.cms.domain.media.entity.MediaCollection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 미디어 컬렉션 매퍼.
 * REQ-MEDIA-005-D
 */
@Mapper
public interface MediaCollectionMapper {

    void insert(MediaCollection collection);

    Optional<MediaCollection> findById(Long id);

    List<MediaCollection> findByOwner(@Param("ownerId") Long ownerId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    List<MediaCollection> findPublic(@Param("offset") int offset,
                                     @Param("limit") int limit);

    void update(MediaCollection collection);

    void deleteById(Long id);
}
