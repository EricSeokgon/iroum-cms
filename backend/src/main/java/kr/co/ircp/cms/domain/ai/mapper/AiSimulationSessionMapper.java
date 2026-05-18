package kr.co.ircp.cms.domain.ai.mapper;

import kr.co.ircp.cms.domain.ai.model.AiSimulationSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * AI 시뮬레이션 세션 MyBatis 매퍼 (UUID PK).
 * SPEC-CMS-AI-001
 */
// @MX:SPEC: SPEC-CMS-AI-001
@Mapper
public interface AiSimulationSessionMapper {

    void insert(AiSimulationSession session);

    Optional<AiSimulationSession> findById(@Param("id") UUID id);

    int updatePdfStatus(@Param("id") UUID id, @Param("pdfStatus") String pdfStatus);

    long countByIpHashSince(@Param("clientIpHash") String clientIpHash,
                            @Param("since") Instant since);
}
