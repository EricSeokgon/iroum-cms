package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 권한 카탈로그 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013-D-2 — permissions 테이블 접근.
 * SQL은 mybatis/mapper/auth/PermissionMapper.xml에 정의.
 */
// @MX:ANCHOR: [AUTO] PermissionMapper — 권한 카탈로그 DB 접근의 핵심 계층
// @MX:REASON: PermissionService, RoleService, AuthServiceImpl 등 fan_in >= 3 참조
@Mapper
public interface PermissionMapper {

    /**
     * 전체 권한 목록 조회.
     *
     * <p>resource, action 오름차순 정렬.
     */
    List<Permission> findAll();

    /**
     * 코드로 권한 단건 조회.
     *
     * @param code 권한 코드 (e.g. USER:READ)
     */
    Optional<Permission> findByCode(@Param("code") String code);
}
