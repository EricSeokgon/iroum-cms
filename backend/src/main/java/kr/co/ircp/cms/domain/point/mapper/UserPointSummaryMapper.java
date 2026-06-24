package kr.co.ircp.cms.domain.point.mapper;

import kr.co.ircp.cms.domain.point.entity.UserPointSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 사용자 포인트 합계 MyBatis Mapper.
 * SPEC-CMS-POINTS-001 REQ-PNT-002~004
 */
@Mapper
public interface UserPointSummaryMapper {

    Optional<UserPointSummary> findByUserId(@Param("userId") Long userId);

    /** 포인트 합계에 delta 를 더한다. 행이 없으면 새로 삽입한다. */
    void upsertDelta(@Param("userId") Long userId, @Param("delta") int delta);
}
