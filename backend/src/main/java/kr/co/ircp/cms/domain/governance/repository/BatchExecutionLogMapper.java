package kr.co.ircp.cms.domain.governance.repository;

import kr.co.ircp.cms.domain.governance.entity.BatchExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 배치 실행 이력 MyBatis 매퍼.
 *
 * <p>SPEC-CMS-009 REQ-DATA-005, REQ-GOV-010.
 */
@Mapper
public interface BatchExecutionLogMapper {

    /** 배치 시작 기록. id가 자동 채워진 {@link BatchExecutionLog}를 반환한다. */
    void insert(BatchExecutionLog log);

    /** 종료 시 status/finished_at/duration_ms/records_processed 등 갱신. */
    void update(BatchExecutionLog log);

    Optional<BatchExecutionLog> findById(@Param("id") Long id);

    List<BatchExecutionLog> findByGroup(@Param("jobGroup") String jobGroup,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);

    /** 필터 + 페이징 조회. params: jobGroup, status, from, to, offset, size */
    List<BatchExecutionLog> findFiltered(@Param("p") Map<String, Object> params);

    int countFiltered(@Param("p") Map<String, Object> params);

    /** 90일 경과 행 삭제 (BatchExecutionLogCleanupJob). */
    int deleteOlderThan(@Param("threshold") Instant threshold);
}
