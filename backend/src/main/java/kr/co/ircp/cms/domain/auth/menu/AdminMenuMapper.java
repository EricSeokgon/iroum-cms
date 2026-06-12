package kr.co.ircp.cms.domain.auth.menu;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 어드민 메뉴 카탈로그 MyBatis Mapper.
 *
 * <p>SPEC-CMS-RBAC-001 REQ-RBAC-002 — admin_menu / admin_menu_permissions 접근.
 * SQL은 mybatis/mapper/auth/AdminMenuMapper.xml에 정의.
 */
// @MX:ANCHOR: [AUTO] AdminMenuMapper — 어드민 메뉴 접근 제어의 DB 접근 계층
// @MX:REASON: AdminMenuService 가 accessible 산출에 사용하는 단일 진실 소스(향후 메뉴 관리 API 확장 시 fan_in 증가)
@Mapper
public interface AdminMenuMapper {

    /**
     * 활성 메뉴 메타 행을 sort_order 순으로 조회 (권한 매핑 제외).
     *
     * @return 활성 메뉴 행 목록
     */
    List<AdminMenuMeta> findActiveMenus();

    /**
     * 메뉴↔권한 매핑 행 전체 조회 (활성 메뉴 한정).
     *
     * @return menu_key ↔ permission_code 매핑 행 목록
     */
    List<AdminMenuPermissionRow> findActiveMenuPermissions();
}
