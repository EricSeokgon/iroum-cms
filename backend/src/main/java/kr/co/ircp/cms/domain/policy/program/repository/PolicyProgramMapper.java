package kr.co.ircp.cms.domain.policy.program.repository;

import kr.co.ircp.cms.domain.policy.program.entity.PolicyProgram;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 정책사업 마스터 MyBatis 매퍼.
 * REQ-POLICY-001
 */
@Mapper
public interface PolicyProgramMapper {

    Optional<PolicyProgram> findById(@Param("id") Long id);

    Optional<PolicyProgram> findByCode(@Param("code") String code);

    List<PolicyProgram> findFiltered(
            @Param("status") String status,
            @Param("industry") String industry,
            @Param("region") String region,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    long countFiltered(
            @Param("status") String status,
            @Param("industry") String industry,
            @Param("region") String region,
            @Param("keyword") String keyword
    );

    /** 매칭 시 활성 + 마감 미경과 정책 풀 (TOP N 매칭에 사용). */
    List<PolicyProgram> findActiveForMatching();

    void insert(PolicyProgram program);

    int update(PolicyProgram program);

    int deleteById(@Param("id") Long id);
}
