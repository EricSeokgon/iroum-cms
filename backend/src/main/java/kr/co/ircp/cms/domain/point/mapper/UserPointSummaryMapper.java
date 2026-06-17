package kr.co.ircp.cms.domain.point.mapper;

import kr.co.ircp.cms.domain.point.entity.UserPointSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 포인트 요약 MyBatis Mapper.
 *
 * <p>SPEC-CMS-POINTS-001 — 누적 총액 upsert + 조회.
 */
@Mapper
public interface UserPointSummaryMapper {

    /** 누적 총액 가산 upsert (없으면 생성, 있으면 total_points += delta). */
    void upsertSummary(@Param("userId") Long userId, @Param("delta") int delta);

    Optional<UserPointSummary> findByUserId(@Param("userId") Long userId);
}
