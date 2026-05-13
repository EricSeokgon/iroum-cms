package kr.co.ircp.cms.domain.content.menu.mapper;

import kr.co.ircp.cms.domain.content.menu.entity.Menu;
import kr.co.ircp.cms.domain.content.menu.entity.MenuPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 메뉴 MyBatis 매퍼.
 * REQ-CONTENT-001-D: 메뉴 트리 CRUD
 * REQ-CONTENT-002-D: 메뉴-권한 매핑
 *
 * // @MX:ANCHOR: [AUTO] MenuMapper — 메뉴 트리 핵심 데이터 접근 계층
 * // @MX:REASON: MenuService, MenuPermissionService에서 fan_in >= 3으로 참조
 */
@Mapper
public interface MenuMapper {

    /** site_id 내 전체 메뉴 조회 (path, sort_order 오름차순) */
    List<Menu> findBySiteId(@Param("siteId") Long siteId);

    /** ID로 단건 조회 */
    Optional<Menu> findById(@Param("id") Long id);

    /** 특정 path prefix로 시작하는 자손 메뉴 조회 (CASCADE 갱신용) */
    List<Menu> findDescendants(@Param("pathPrefix") String pathPrefix);

    /** 코드 유일성 확인 */
    boolean existsBySiteIdAndCode(@Param("siteId") Long siteId, @Param("code") String code);

    /** 메뉴 생성 */
    void insert(Menu menu);

    /** 메뉴 수정 */
    int update(Menu menu);

    /** path와 depth 일괄 갱신 (이동 후 자손 갱신) */
    int updatePathAndDepth(@Param("id") Long id, @Param("path") String path, @Param("depth") short depth);

    /** 가시성 토글 */
    int updateVisibility(@Param("id") Long id, @Param("isVisible") boolean isVisible);

    /** 정렬 순서 갱신 */
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") int sortOrder);

    /** 메뉴 삭제 (ON DELETE CASCADE로 자손 포함) */
    int deleteById(@Param("id") Long id);

    // ─── 메뉴-권한 매핑 ───────────────────────────────────────────────────────

    /** 메뉴의 현재 권한 코드 목록 조회 */
    List<String> findPermissionCodesByMenuId(@Param("menuId") Long menuId);

    /** 메뉴-권한 매핑 전체 삭제 (replace 전처리) */
    int deletePermissionsByMenuId(@Param("menuId") Long menuId);

    /** 메뉴-권한 매핑 일괄 INSERT */
    void insertPermissions(@Param("permissions") List<MenuPermission> permissions);
}
