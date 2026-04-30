package kr.co.ircp.cms.domain.system.code.mapper;

import kr.co.ircp.cms.domain.system.code.entity.CodeGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 공통코드 그룹 MyBatis Mapper.
 * REQ-SYSTEM-004-D
 */
@Mapper
public interface CodeGroupMapper {

    void insert(CodeGroup codeGroup);

    Optional<CodeGroup> findById(@Param("id") Long id);

    Optional<CodeGroup> findByGroupCode(@Param("groupCode") String groupCode);

    List<CodeGroup> findAll();

    void update(CodeGroup codeGroup);

    void delete(@Param("id") Long id);

    boolean existsById(@Param("id") Long id);

    /** 그룹에 코드가 존재하는지 확인 (RESTRICT 검사용) */
    boolean hasCodesInGroup(@Param("groupCode") String groupCode);
}
