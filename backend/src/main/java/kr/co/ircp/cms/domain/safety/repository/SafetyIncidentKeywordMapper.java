package kr.co.ircp.cms.domain.safety.repository;

import kr.co.ircp.cms.domain.safety.entity.SafetyIncidentKeyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 사고-키워드 매핑 MyBatis 매퍼.
 * REQ-SAFETY-001-D-3
 */
@Mapper
public interface SafetyIncidentKeywordMapper {

    void insert(SafetyIncidentKeyword mapping);

    int deleteByIncidentId(@Param("incidentId") Long incidentId);

    /** 특정 사고사례에 매핑된 (keyword + category + weight) 목록. */
    List<SafetyIncidentKeyword> findKeywordsByIncidentId(@Param("incidentId") Long incidentId);
}
