package kr.co.ircp.cms.domain.safety.repository;

import kr.co.ircp.cms.domain.safety.entity.SafetyChecklistItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 체크리스트 항목 MyBatis 매퍼.
 * REQ-SAFETY-004 + 005
 */
@Mapper
public interface SafetyChecklistItemMapper {

    List<SafetyChecklistItem> findByTemplateId(@Param("templateId") Long templateId);

    Optional<SafetyChecklistItem> findById(@Param("id") Long id);

    void insert(SafetyChecklistItem item);

    int update(SafetyChecklistItem item);

    int deleteById(@Param("id") Long id);
}
