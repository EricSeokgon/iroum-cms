package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.entity.OrganizationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 조직 변경 이력 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014-D-4 — organization_history 테이블 접근.
 * SQL은 mybatis/mapper/auth/OrganizationHistoryMapper.xml에 정의.
 */
@Mapper
public interface OrganizationHistoryMapper {

    /**
     * 이력 항목 삽입.
     *
     * <p>version은 findMaxVersion + 1로 서비스 레이어에서 계산 후 전달.
     */
    void insert(OrganizationHistory entry);

    /**
     * 조직의 전체 이력 목록 조회 (버전 내림차순).
     */
    List<OrganizationHistory> findByOrgId(@Param("orgId") long orgId);

    /**
     * 조직의 현재 최대 버전 번호 조회.
     *
     * <p>이력이 없으면 0 반환.
     */
    int findMaxVersion(@Param("orgId") long orgId);
}
