package kr.co.ircp.cms.domain.auth.dto;

import java.util.List;

/**
 * 조직 트리 노드 응답 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — GET /api/v1/organizations/tree 응답.
 * children은 재귀적으로 동일 타입을 포함.
 */
public record OrganizationTreeNode(
        long id,
        String code,
        String name,
        int depth,
        int sortOrder,
        String status,
        List<OrganizationTreeNode> children
) {}
