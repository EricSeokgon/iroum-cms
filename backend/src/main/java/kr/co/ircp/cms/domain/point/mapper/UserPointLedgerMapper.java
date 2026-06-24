package kr.co.ircp.cms.domain.point.mapper;

import kr.co.ircp.cms.domain.point.entity.UserPointLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 포인트 원장 MyBatis Mapper.
 * SPEC-CMS-POINTS-001 REQ-PNT-002~004, REQ-PNT-007
 */
@Mapper
public interface UserPointLedgerMapper {

    void insert(UserPointLedger ledger);

    List<UserPointLedger> findAll(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    int countAll(@Param("userId") Long userId);
}
