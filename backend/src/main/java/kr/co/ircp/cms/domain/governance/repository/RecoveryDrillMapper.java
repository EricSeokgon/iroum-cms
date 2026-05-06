package kr.co.ircp.cms.domain.governance.repository;

import kr.co.ircp.cms.domain.governance.entity.RecoveryDrillLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 복구 시험 이력 매퍼.
 *
 * <p>SPEC-CMS-009 REQ-GOV-011~012.
 */
@Mapper
public interface RecoveryDrillMapper {

    void insert(RecoveryDrillLog log);

    Optional<RecoveryDrillLog> findById(@Param("id") Long id);

    List<RecoveryDrillLog> findByDateRange(@Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** 필터: drillType, result, year. */
    List<RecoveryDrillLog> findFiltered(@Param("p") Map<String, Object> params);

    /** 최근 N일 동안의 등록 건수 (RecoveryDrillReminderJob). */
    int countRecentDrills(@Param("from") LocalDate from);
}
