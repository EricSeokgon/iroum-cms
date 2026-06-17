package kr.co.ircp.cms.domain.point.mapper;

import kr.co.ircp.cms.domain.point.entity.UserPointLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 포인트 원장 MyBatis Mapper.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-002/003/004/006.
 */
@Mapper
public interface UserPointLedgerMapper {

    /** 적립 1건 기록 (useGeneratedKeys로 id 채움). */
    void insert(UserPointLedger ledger);

    /** 사용자 본인 내역 페이징 조회 (REQ-PNT-006). */
    List<UserPointLedger> findByUserId(@Param("userId") Long userId,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /** 사용자 본인 내역 총 개수. */
    long countByUserId(@Param("userId") Long userId);

    /** 관리자 필터 조회 (userId/eventType/기간, 모두 nullable) — REQ-PNT-006. */
    List<UserPointLedger> findByFilter(@Param("userId") Long userId,
                                       @Param("eventType") String eventType,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /** 관리자 필터 총 개수. */
    long countByFilter(@Param("userId") Long userId,
                       @Param("eventType") String eventType,
                       @Param("from") Instant from,
                       @Param("to") Instant to);
}
