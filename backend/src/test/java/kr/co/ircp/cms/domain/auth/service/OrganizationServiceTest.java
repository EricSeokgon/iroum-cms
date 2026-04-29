package kr.co.ircp.cms.domain.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.auth.dto.OrganizationCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.OrganizationDetail;
import kr.co.ircp.cms.domain.auth.dto.OrganizationHistoryEntry;
import kr.co.ircp.cms.domain.auth.dto.OrganizationSummary;
import kr.co.ircp.cms.domain.auth.dto.OrganizationTreeNode;
import kr.co.ircp.cms.domain.auth.dto.OrganizationUpdateRequest;
import kr.co.ircp.cms.domain.auth.entity.Organization;
import kr.co.ircp.cms.domain.auth.entity.OrganizationHistory;
import kr.co.ircp.cms.domain.auth.entity.OrganizationStatus;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.exception.CyclicReferenceException;
import kr.co.ircp.cms.domain.auth.exception.DepthExceededException;
import kr.co.ircp.cms.domain.auth.exception.DuplicateOrganizationCodeException;
import kr.co.ircp.cms.domain.auth.exception.OrganizationHasChildrenException;
import kr.co.ircp.cms.domain.auth.exception.OrganizationHasUsersException;
import kr.co.ircp.cms.domain.auth.exception.OrganizationNotFoundException;
import kr.co.ircp.cms.domain.auth.exception.UserNotFoundException;
import kr.co.ircp.cms.domain.auth.repository.OrganizationHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.OrganizationMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrganizationService 단위 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — Mockito 기반 서비스 레이어 검증. 12개 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService 단위 테스트")
class OrganizationServiceTest {

    @Mock private OrganizationMapper orgMapper;
    @Mock private OrganizationHistoryMapper histMapper;
    @Mock private UserMapper userMapper;

    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationServiceImpl(orgMapper, histMapper, userMapper, new ObjectMapper());
    }

    // ─── create 테스트 ────────────────────────────────────────────

    @Test
    @DisplayName("create — 루트 하위 조직 생성 성공")
    void create_succeeds_underRoot() {
        // given
        Organization root = rootOrg();
        when(orgMapper.existsByCode("DEPT_A")).thenReturn(false);
        when(orgMapper.findById(1L)).thenReturn(Optional.of(root));
        when(histMapper.findMaxVersion(anyLong())).thenReturn(0);
        // insert 호출 시 id 주입 시뮬레이션
        doAnswer(inv -> {
            Organization o = inv.getArgument(0);
            o.setId(10L);
            return null;
        }).when(orgMapper).insert(any(Organization.class));

        OrganizationCreateRequest req = new OrganizationCreateRequest(
                "DEPT_A", "개발팀", null, 1L, 0);

        // when
        OrganizationDetail result = service.create(req, 1L);

        // then
        assertThat(result.code()).isEqualTo("DEPT_A");
        assertThat(result.depth()).isEqualTo(1);
        assertThat(result.path()).isEqualTo("/1/10/");
        verify(orgMapper).updatePath(10L, "/1/10/");
        verify(histMapper).insert(any(OrganizationHistory.class));
    }

    @Test
    @DisplayName("create — 코드 중복 시 DuplicateOrganizationCodeException")
    void create_throwsDuplicateCode() {
        when(orgMapper.existsByCode("ROOT")).thenReturn(true);

        assertThatThrownBy(() ->
                service.create(new OrganizationCreateRequest("ROOT", "루트", null, null, 0), 1L))
                .isInstanceOf(DuplicateOrganizationCodeException.class);

        verify(orgMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create — depth 6 초과 시 DepthExceededException")
    void create_throwsDepthExceeded_atDepth6() {
        // depth=5인 부모 (최대)
        Organization deepParent = Organization.builder()
                .id(99L).code("D5").name("D5").depth(5).path("/1/2/3/4/5/99/")
                .status(OrganizationStatus.ACTIVE).sortOrder(0).build();

        when(orgMapper.existsByCode("D6")).thenReturn(false);
        when(orgMapper.findById(99L)).thenReturn(Optional.of(deepParent));

        assertThatThrownBy(() ->
                service.create(new OrganizationCreateRequest("D6", "D6", null, 99L, 0), 1L))
                .isInstanceOf(DepthExceededException.class)
                .hasMessageContaining("6");
    }

    // ─── update 테스트 ────────────────────────────────────────────

    @Test
    @DisplayName("update — 부모 변경 시 path 및 depth 재계산")
    void update_movesNode_recalculatesPath() {
        Organization org = Organization.builder()
                .id(10L).code("DEPT_A").name("개발팀").parentId(1L)
                .depth(1).path("/1/10/").status(OrganizationStatus.ACTIVE).sortOrder(0).build();
        Organization newParent = Organization.builder()
                .id(2L).code("HQ").name("본부2").depth(0).path("/2/")
                .status(OrganizationStatus.ACTIVE).sortOrder(0).build();

        when(orgMapper.findById(10L)).thenReturn(Optional.of(org));
        when(orgMapper.findById(2L)).thenReturn(Optional.of(newParent));
        when(histMapper.findMaxVersion(10L)).thenReturn(1);

        OrganizationUpdateRequest req = new OrganizationUpdateRequest(null, null, 2L, null, null);
        OrganizationDetail result = service.update(10L, req, 1L);

        assertThat(result.parentId()).isEqualTo(2L);
        assertThat(result.depth()).isEqualTo(1);
        assertThat(result.path()).isEqualTo("/2/10/");
        verify(orgMapper).updateDescendantPaths("/1/10/", "/2/10/");
    }

    @Test
    @DisplayName("update — 자신의 자손으로 이동 시 CyclicReferenceException")
    void update_throwsCyclicReference_whenMovingToOwnDescendant() {
        Organization org = Organization.builder()
                .id(1L).code("ROOT").name("루트").parentId(null)
                .depth(0).path("/1/").status(OrganizationStatus.ACTIVE).sortOrder(0).build();

        when(orgMapper.findById(1L)).thenReturn(Optional.of(org));

        // id=5는 /1/의 자손 (path에 /5/ 포함)
        Organization descendant = Organization.builder()
                .id(5L).code("D").name("D").depth(1).path("/1/5/")
                .status(OrganizationStatus.ACTIVE).sortOrder(0).build();
        // path=/1/ 에 /5/ 포함 여부: /1/에 "/5/"가 없으므로 CyclicReferenceException을 위해
        // org.path에 5가 포함되도록 셋업
        Organization orgWithChild = Organization.builder()
                .id(1L).code("ROOT").name("루트").parentId(null)
                .depth(0).path("/1/5/").status(OrganizationStatus.ACTIVE).sortOrder(0).build();
        when(orgMapper.findById(1L)).thenReturn(Optional.of(orgWithChild));

        assertThatThrownBy(() ->
                service.update(1L, new OrganizationUpdateRequest(null, null, 5L, null, null), 1L))
                .isInstanceOf(CyclicReferenceException.class);
    }

    // ─── delete 테스트 ────────────────────────────────────────────

    @Test
    @DisplayName("delete — 자식 노드 존재 시 OrganizationHasChildrenException")
    void delete_throwsHasChildren() {
        when(orgMapper.findById(1L)).thenReturn(Optional.of(rootOrg()));
        when(orgMapper.countActiveChildren(1L)).thenReturn(3);

        assertThatThrownBy(() -> service.delete(1L, 1L))
                .isInstanceOf(OrganizationHasChildrenException.class);

        verify(orgMapper, never()).softDelete(anyLong(), any());
    }

    @Test
    @DisplayName("delete — 소속 사용자 존재 시 OrganizationHasUsersException")
    void delete_throwsHasUsers() {
        when(orgMapper.findById(1L)).thenReturn(Optional.of(rootOrg()));
        when(orgMapper.countActiveChildren(1L)).thenReturn(0);
        when(orgMapper.countAttachedUsers(1L)).thenReturn(2);

        assertThatThrownBy(() -> service.delete(1L, 1L))
                .isInstanceOf(OrganizationHasUsersException.class);
    }

    @Test
    @DisplayName("delete — 리프 노드이고 사용자 없으면 소프트 삭제 성공")
    void delete_succeeds_whenLeafAndEmpty() {
        when(orgMapper.findById(10L)).thenReturn(Optional.of(leafOrg()));
        when(orgMapper.countActiveChildren(10L)).thenReturn(0);
        when(orgMapper.countAttachedUsers(10L)).thenReturn(0);
        when(histMapper.findMaxVersion(10L)).thenReturn(1);

        service.delete(10L, 1L);

        verify(orgMapper).softDelete(eq(10L), any(Instant.class));
        verify(histMapper).insert(any(OrganizationHistory.class));
    }

    // ─── assignUser 테스트 ────────────────────────────────────────

    @Test
    @DisplayName("assignUser — 사용자 조직 배정 성공")
    void assignUser_updatesOrganizationId() {
        when(userMapper.findById(42L)).thenReturn(Optional.of(testUser()));
        when(orgMapper.findById(1L)).thenReturn(Optional.of(rootOrg()));

        service.assignUser(42L, 1L, 1L);

        verify(userMapper).updateOrganization(eq(42L), eq(1L), any(Instant.class));
    }

    @Test
    @DisplayName("assignUser — organizationId=null이면 배정 해제")
    void assignUser_setNull_clearsOrganization() {
        when(userMapper.findById(42L)).thenReturn(Optional.of(testUser()));

        service.assignUser(42L, null, 1L);

        verify(userMapper).updateOrganization(eq(42L), isNull(), any(Instant.class));
        verify(orgMapper, never()).findById(anyLong());
    }

    // ─── getTree 테스트 ───────────────────────────────────────────

    @Test
    @DisplayName("getTree — 계층 구조 반환")
    void getTree_returnsHierarchy() {
        Organization root = rootOrg();
        Organization child = Organization.builder()
                .id(2L).code("CHILD").name("자식").parentId(1L)
                .depth(1).path("/1/2/").status(OrganizationStatus.ACTIVE).sortOrder(0).build();

        when(orgMapper.findAll("ACTIVE")).thenReturn(List.of(root, child));

        List<OrganizationTreeNode> tree = service.getTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isEqualTo(1L);
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).id()).isEqualTo(2L);
    }

    // ─── getHistory 테스트 ────────────────────────────────────────

    @Test
    @DisplayName("getHistory — 버전 이력 반환")
    void getHistory_returnsVersionedSnapshots() {
        when(orgMapper.findById(1L)).thenReturn(Optional.of(rootOrg()));
        OrganizationHistory h1 = OrganizationHistory.builder()
                .id(1L).orgId(1L).version(1).snapshot("{\"id\":1}")
                .changedBy(1L).changedAt(Instant.now()).changeSummary("CREATE").build();
        when(histMapper.findByOrgId(1L)).thenReturn(List.of(h1));

        List<OrganizationHistoryEntry> entries = service.getHistory(1L);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).version()).isEqualTo(1);
        assertThat(entries.get(0).changeSummary()).isEqualTo("CREATE");
    }

    // ─── 픽스처 ──────────────────────────────────────────────────

    private Organization rootOrg() {
        return Organization.builder()
                .id(1L).code("ROOT").name("본부").parentId(null)
                .depth(0).path("/1/").status(OrganizationStatus.ACTIVE).sortOrder(0)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private Organization leafOrg() {
        return Organization.builder()
                .id(10L).code("LEAF").name("리프").parentId(1L)
                .depth(1).path("/1/10/").status(OrganizationStatus.ACTIVE).sortOrder(0)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private User testUser() {
        return User.builder()
                .id(42L).username("tester").email("test@example.com")
                .name("테스터").status(UserStatus.ACTIVE)
                .passwordHash("$2a$12$hash").failCount(0)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
