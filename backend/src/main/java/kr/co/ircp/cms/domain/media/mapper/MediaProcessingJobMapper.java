package kr.co.ircp.cms.domain.media.mapper;

import kr.co.ircp.cms.domain.media.entity.JobStatus;
import kr.co.ircp.cms.domain.media.entity.MediaProcessingJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 미디어 후처리 작업 큐 매퍼.
 * REQ-MEDIA-002-D
 */
@Mapper
public interface MediaProcessingJobMapper {

    void insert(MediaProcessingJob job);

    void insertAll(@Param("jobs") List<MediaProcessingJob> jobs);

    List<MediaProcessingJob> findPending(@Param("limit") int limit);

    void updateStatus(@Param("id") Long id,
                      @Param("status") JobStatus status,
                      @Param("errorMessage") String errorMessage);
}
