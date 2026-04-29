package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.dto.RoleSummary;
import kr.co.ircp.cms.domain.auth.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 역할 마스터 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — roles 테이블 접근.
 * SQL은 mybatis/mapper/auth/RoleMapper.xml에 정의.
 */
// @MX:ANCHOR: [AUTO] RoleMapper — 역할 마스터 DB 접근의 핵심 계층
// @MX:REASON: RoleService, PermissionService, AuthServiceImpl 등 fan_in >= 3 참조
@Mapper
public interface RoleMapper {

    /**
     * 전체 역할 목록 조회 (user_count, permission_count JOIN 포함).
     */
    List<RoleSummary> findAll();

    /**
     * 코드로 역할 단건 조회.
     *
     * @param code 역할 코드
     */
    Optional<Role> findByCode(@Param("code") String code);

    /**
     * 역할 신규 삽입.
     *
     * @param role 역할 엔티티
     */
    void insert(Role role);

    /**
     * 역할 정보 수정 (name, description).
     *
     * @param role 수정할 역할 (code는 WHERE 조건)
     */
    void update(Role role);

    /**
     * 역할 삭제 (is_system=false이고 사용자 매핑 없을 때만 호출).
     *
     * @param code 역할 코드
     */
    void delete(@Param("code") String code);

    /**
     * 역할 코드 존재 여부 확인.
     *
     * @param code 역할 코드
     */
    boolean existsByCode(@Param("code") String code);

    /**
     * 역할에 배정된 사용자 수 조회.
     *
     * @param roleCode 역할 코드
     */
    int countUsers(@Param("roleCode") String roleCode);
}
