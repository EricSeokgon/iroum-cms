package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.OrganizationCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.OrganizationDetail;
import kr.co.ircp.cms.domain.auth.dto.OrganizationHistoryEntry;
import kr.co.ircp.cms.domain.auth.dto.OrganizationSummary;
import kr.co.ircp.cms.domain.auth.dto.OrganizationTreeNode;
import kr.co.ircp.cms.domain.auth.dto.OrganizationUpdateRequest;

import java.util.List;

/**
 * 부서·조직 관리 서비스 인터페이스.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — 조직 CRUD, 트리 조회, 이력 조회, 사용자 배정.
 */
// @MX:ANCHOR: [AUTO] OrganizationService — 조직 관리 비즈니스 계층의 공개 계약
// @MX:REASON: OrganizationController, UserOrganizationController 등 3개 이상 호출 지점 (fan_in >= 3)
public interface OrganizationService {

    /**
     * 조직 트리 조회 (ACTIVE 조직만, 재귀 계층 구조).
     */
    List<OrganizationTreeNode> getTree();

    /**
     * 조직 목록 조회 (flat, 상태 필터).
     *
     * @param status 상태 필터 (null이면 전체)
     */
    List<OrganizationSummary> findAll(String status);

    /**
     * 조직 단건 조회.
     *
     * @throws kr.co.ircp.cms.domain.auth.exception.OrganizationNotFoundException 조직 미존재
     */
    OrganizationDetail findById(long id);

    /**
     * 조직 생성.
     *
     * @param req     생성 요청 DTO
     * @param actorId 수행 사용자 PK
     * @throws kr.co.ircp.cms.domain.auth.exception.DuplicateOrganizationCodeException 코드 중복
     * @throws kr.co.ircp.cms.domain.auth.exception.OrganizationNotFoundException      부모 조직 미존재
     * @throws kr.co.ircp.cms.domain.auth.exception.DepthExceededException             깊이 초과
     */
    OrganizationDetail create(OrganizationCreateRequest req, long actorId);

    /**
     * 조직 수정.
     *
     * @param id      수정 대상 PK
     * @param req     수정 요청 DTO
     * @param actorId 수행 사용자 PK
     * @throws kr.co.ircp.cms.domain.auth.exception.OrganizationNotFoundException 조직 미존재
     * @throws kr.co.ircp.cms.domain.auth.exception.CyclicReferenceException     순환 참조
     * @throws kr.co.ircp.cms.domain.auth.exception.DepthExceededException       깊이 초과
     */
    OrganizationDetail update(long id, OrganizationUpdateRequest req, long actorId);

    /**
     * 조직 소프트 삭제.
     *
     * @param id      삭제 대상 PK
     * @param actorId 수행 사용자 PK
     * @throws kr.co.ircp.cms.domain.auth.exception.OrganizationNotFoundException   조직 미존재
     * @throws kr.co.ircp.cms.domain.auth.exception.OrganizationHasChildrenException 자식 조직 존재
     * @throws kr.co.ircp.cms.domain.auth.exception.OrganizationHasUsersException   소속 사용자 존재
     */
    void delete(long id, long actorId);

    /**
     * 조직 변경 이력 조회 (버전 내림차순).
     */
    List<OrganizationHistoryEntry> getHistory(long orgId);

    /**
     * 사용자의 소속 조직 변경.
     *
     * @param userId         대상 사용자 PK
     * @param organizationId 배정할 조직 PK (null이면 해제)
     * @param actorId        수행 사용자 PK
     * @throws kr.co.ircp.cms.domain.auth.exception.UserNotFoundException         사용자 미존재
     * @throws kr.co.ircp.cms.domain.auth.exception.OrganizationNotFoundException 조직 미존재 (non-null인 경우)
     */
    void assignUser(long userId, Long organizationId, long actorId);
}
