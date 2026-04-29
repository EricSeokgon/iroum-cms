package kr.co.ircp.cms.integration.auth;

import kr.co.ircp.cms.domain.auth.entity.Organization;
import kr.co.ircp.cms.domain.auth.entity.OrganizationStatus;
import kr.co.ircp.cms.domain.auth.repository.OrganizationMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrganizationMapper 통합 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — materialized path 기반 조직 트리 검증.
 */
@Transactional
class OrganizationMapperIT extends AbstractIntegrationTest {

    @Autowired
    private OrganizationMapper organizationMapper;

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private Organization buildRoot(String code, String name) {
        return Organization.builder()
                .code(code)
                .name(name)
                .depth(0)
                .sortOrder(1)
                .status(OrganizationStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Organization buildChild(String code, String name, long parentId, int depth) {
        return Organization.builder()
                .code(code)
                .name(name)
                .parentId(parentId)
                .depth(depth)
                .sortOrder(1)
                .status(OrganizationStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ──────────────────────────────────────────────
    // 테스트
    // ──────────────────────────────────────────────

    @Test
    void insert_andFindByCode() {
        // given
        Organization root = buildRoot("ORG_IT_ROOT", "통합테스트루트");
        organizationMapper.insert(root);
        // 루트 path = /{id}/
        organizationMapper.updatePath(root.getId(), "/" + root.getId() + "/");

        // when
        Optional<Organization> found = organizationMapper.findByCode("ORG_IT_ROOT");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("통합테스트루트");
        assertThat(found.get().getPath()).isEqualTo("/" + root.getId() + "/");
    }

    @Test
    void findChildren_returnsHierarchy() {
        // given — 루트 → 자식 2개
        Organization root = buildRoot("ORG_IT_P", "부모조직");
        organizationMapper.insert(root);
        organizationMapper.updatePath(root.getId(), "/" + root.getId() + "/");

        Organization child1 = buildChild("ORG_IT_C1", "자식조직1", root.getId(), 1);
        Organization child2 = buildChild("ORG_IT_C2", "자식조직2", root.getId(), 1);
        organizationMapper.insert(child1);
        organizationMapper.insert(child2);

        // when
        List<Organization> children = organizationMapper.findChildren(root.getId());

        // then
        assertThat(children).hasSize(2);
        assertThat(children).extracting(Organization::getCode)
                .containsExactlyInAnyOrder("ORG_IT_C1", "ORG_IT_C2");
    }

    @Test
    void findDescendants_filtersByPathPrefix() {
        // given — 루트 → 자식 → 손자 구조
        Organization root = buildRoot("ORG_IT_ANCS", "조상조직");
        organizationMapper.insert(root);
        String rootPath = "/" + root.getId() + "/";
        organizationMapper.updatePath(root.getId(), rootPath);

        Organization child = buildChild("ORG_IT_CHILD", "자식조직", root.getId(), 1);
        organizationMapper.insert(child);
        String childPath = rootPath + child.getId() + "/";
        organizationMapper.updatePath(child.getId(), childPath);

        Organization grandchild = buildChild("ORG_IT_GC", "손자조직", child.getId(), 2);
        organizationMapper.insert(grandchild);
        organizationMapper.updatePath(grandchild.getId(), childPath + grandchild.getId() + "/");

        // when — 루트 path prefix로 자손 전체 검색
        List<Organization> descendants = organizationMapper.findDescendants(rootPath);

        // then — 자식 + 손자 2개 (루트 제외)
        assertThat(descendants).hasSizeGreaterThanOrEqualTo(2);
        assertThat(descendants).extracting(Organization::getCode)
                .contains("ORG_IT_CHILD", "ORG_IT_GC");
    }
}
