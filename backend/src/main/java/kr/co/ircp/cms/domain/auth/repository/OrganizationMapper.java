package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.entity.Organization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 조직 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — organization 테이블 접근.
 * SQL은 mybatis/mapper/auth/OrganizationMapper.xml에 정의.
 */
// @MX:ANCHOR: [AUTO] OrganizationMapper — 조직 트리 DB 접근의 핵심 계층
// @MX:REASON: OrganizationServiceImpl.create/update/delete/getTree 등 3개 이상 메서드에서 참조 (fan_in >= 3)
@Mapper
public interface OrganizationMapper {

    /**
     * PK로 조직 단건 조회 (소프트 삭제 제외).
     */
    Optional<Organization> findById(@Param("id") long id);

    /**
     * 코드로 조직 조회 (소프트 삭제 제외).
     */
    Optional<Organization> findByCode(@Param("code") String code);

    /**
     * 전체 조직 목록 조회 (flat).
     *
     * @param status 상태 필터 (null이면 전체)
     */
    List<Organization> findAll(@Param("status") String status);

    /**
     * 직접 자식 조직 목록 조회.
     */
    List<Organization> findChildren(@Param("parentId") long parentId);

    /**
     * 자손 조직 목록 조회 (path prefix 검색).
     *
     * <p>path LIKE '{prefix}%'로 모든 자손 일괄 조회.
     */
    List<Organization> findDescendants(@Param("path") String pathPrefix);

    /**
     * 조직 신규 삽입.
     *
     * <p>id는 BIGSERIAL 자동 생성. useGeneratedKeys로 채움.
     * insert 후 path를 /{id}/로 갱신하는 updatePath를 별도 호출해야 함 (루트가 아닌 경우).
     */
    void insert(Organization org);

    /**
     * path 컬럼만 갱신 (insert 후 id 확정 후 path 재설정용).
     */
    void updatePath(@Param("id") long id, @Param("path") String path);

    /**
     * 조직 정보 수정.
     */
    void update(Organization org);

    /**
     * 자손 조직의 path를 일괄 갱신.
     *
     * <p>노드 이동 시 자손 path를 oldPrefix → newPrefix로 일괄 치환.
     */
    // @MX:WARN: [AUTO] updateDescendantPaths — 자손 대량 UPDATE (이동 시 전체 서브트리 영향)
    // @MX:REASON: 자손 경로 일괄 갱신 실패 시 path 불일치로 트리 조회가 잘못됨
    void updateDescendantPaths(
            @Param("oldPrefix") String oldPrefix,
            @Param("newPrefix") String newPrefix);

    /**
     * 소프트 삭제 (deleted_at, status='DELETED' 동시 갱신).
     */
    void softDelete(@Param("id") long id, @Param("deletedAt") Instant when);

    /**
     * 코드 중복 여부 확인 (소프트 삭제 포함).
     */
    boolean existsByCode(@Param("code") String code);

    /**
     * 직접 자식 조직 수 조회 (ACTIVE 상태, 소프트 삭제 제외).
     */
    int countActiveChildren(@Param("parentId") long parentId);

    /**
     * 조직에 소속된 활성 사용자 수 조회.
     */
    int countAttachedUsers(@Param("orgId") long orgId);
}
