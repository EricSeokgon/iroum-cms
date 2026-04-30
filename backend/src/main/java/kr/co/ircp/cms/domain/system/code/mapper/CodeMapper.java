package kr.co.ircp.cms.domain.system.code.mapper;

import kr.co.ircp.cms.domain.system.code.entity.Code;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 공통코드 MyBatis Mapper.
 * REQ-SYSTEM-004-D
 */
@Mapper
public interface CodeMapper {

    void insert(Code code);

    Optional<Code> findById(@Param("id") Long id);

    /** ACTIVE 코드만 sort_order ASC */
    List<Code> findActiveByGroupCode(@Param("groupCode") String groupCode);

    /** 여러 그룹 일괄 조회 */
    List<Code> findActiveByGroupCodes(@Param("groupCodes") List<String> groupCodes);

    void update(Code code);

    void delete(@Param("id") Long id);

    boolean existsByGroupCodeAndCode(@Param("groupCode") String groupCode,
                                     @Param("code") String code);

    boolean existsByGroupCodeAndCodeExcludeId(@Param("groupCode") String groupCode,
                                               @Param("code") String code,
                                               @Param("excludeId") Long excludeId);
}
