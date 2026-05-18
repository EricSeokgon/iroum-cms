package kr.co.ircp.cms.domain.ai.mapper;

import kr.co.ircp.cms.domain.ai.model.AiRetrainQueue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * AI 재학습 큐 MyBatis 매퍼.
 * SPEC-CMS-AI-001
 */
// @MX:SPEC: SPEC-CMS-AI-001
@Mapper
public interface AiRetrainQueueMapper {

    void insert(AiRetrainQueue item);

    Optional<AiRetrainQueue> findById(@Param("id") Long id);

    List<AiRetrainQueue> findQueued();

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
