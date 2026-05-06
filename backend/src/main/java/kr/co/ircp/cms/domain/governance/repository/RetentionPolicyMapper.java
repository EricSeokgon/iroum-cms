package kr.co.ircp.cms.domain.governance.repository;

import kr.co.ircp.cms.domain.governance.entity.RetentionPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 보존 정책 MyBatis 매퍼.
 *
 * <p>SPEC-CMS-009 REQ-GOV-006~009.
 */
@Mapper
public interface RetentionPolicyMapper {

    Optional<RetentionPolicy> findById(@Param("id") Long id);

    Optional<RetentionPolicy> findByTargetTable(@Param("targetTable") String targetTable);

    List<RetentionPolicy> findAll();

    void insert(RetentionPolicy policy);

    void update(RetentionPolicy policy);
}
