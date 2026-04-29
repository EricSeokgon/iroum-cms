package kr.co.ircp.cms.domain.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.auth.dto.OrganizationCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.OrganizationDetail;
import kr.co.ircp.cms.domain.auth.dto.OrganizationHistoryEntry;
import kr.co.ircp.cms.domain.auth.dto.OrganizationSummary;
import kr.co.ircp.cms.domain.auth.dto.OrganizationTreeNode;
import kr.co.ircp.cms.domain.auth.dto.OrganizationUpdateRequest;
import kr.co.ircp.cms.domain.auth.entity.Organization;
import kr.co.ircp.cms.domain.auth.entity.OrganizationHistory;
import kr.co.ircp.cms.domain.auth.entity.OrganizationStatus;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 부서·조직 관리 서비스 구현체.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — materialized path 패턴으로 조직 트리를 관리.
 * 단일 SELECT 후 Java 메모리에서 재귀 트리를 구성한다 (수십~수백 노드 가정).
 */
// @MX:ANCHOR: [AUTO] OrganizationServiceImpl — 조직 CRUD 핵심 구현
// @MX:REASON: create/update/delete/getTree가 OrganizationMapper, OrganizationHistoryMapper, UserMapper를 모두 참조 (fan_in >= 3)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationServiceImpl implements OrganizationService {

    private static final int MAX_DEPTH = 5;

    private final OrganizationMapper orgMapper;
    private final OrganizationHistoryMapper histMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<OrganizationTreeNode> getTree() {
        List<Organization> all = orgMapper.findAll(OrganizationStatus.ACTIVE.name());
        return buildTree(all, null);
    }

    @Override
    public List<OrganizationSummary> findAll(String status) {
        return orgMapper.findAll(status).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    public OrganizationDetail findById(long id) {
        return orgMapper.findById(id)
                .map(this::toDetail)
                .orElseThrow(() -> new OrganizationNotFoundException(id));
    }

    @Override
    @Transactional
    @AuditLog(action = "CREATE", entityType = "Organization")
    public OrganizationDetail create(OrganizationCreateRequest req, long actorId) {
        if (orgMapper.existsByCode(req.code())) {
            throw new DuplicateOrganizationCodeException(req.code());
        }

        int depth = 0;
        String parentPath = "/";

        if (req.parentId() != null) {
            Organization parent = orgMapper.findById(req.parentId())
                    .orElseThrow(() -> new OrganizationNotFoundException(req.parentId()));
            depth = parent.getDepth() + 1;
            parentPath = parent.getPath();
        }

        if (depth > MAX_DEPTH) {
            throw new DepthExceededException(depth);
        }

        Organization org = Organization.builder()
                .code(req.code())
                .name(req.name())
                .description(req.description())
                .parentId(req.parentId())
                .depth(depth)
                .path(parentPath)  // 임시 경로, id 확정 후 갱신
                .sortOrder(req.sortOrder())
                .status(OrganizationStatus.ACTIVE)
                .build();

        orgMapper.insert(org);
        // id 확정 후 path 갱신: /{parentPath...}{id}/
        String finalPath = parentPath + org.getId() + "/";
        orgMapper.updatePath(org.getId(), finalPath);
        org.setPath(finalPath);

        insertHistory(org, "CREATE", actorId);
        return toDetail(org);
    }

    @Override
    @Transactional
    // @MX:WARN: [AUTO] update — 노드 이동 시 자손 path 일괄 갱신으로 서브트리 전체 영향
    // @MX:REASON: updateDescendantPaths가 path LIKE prefix로 대량 UPDATE를 실행하므로 깊은 서브트리에서 성능 주의
    @AuditLog(action = "UPDATE", entityType = "Organization")
    public OrganizationDetail update(long id, OrganizationUpdateRequest req, long actorId) {
        Organization org = orgMapper.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));

        String oldPath = org.getPath();
        int newDepth = org.getDepth();
        String newPath = org.getPath();

        if (req.parentId() != null && !req.parentId().equals(org.getParentId())) {
            // 부모 변경 시 순환 참조 검사: 새 부모가 자신의 자손이면 안 됨
            if (isDescendant(req.parentId(), org.getPath())) {
                throw new CyclicReferenceException(id, req.parentId());
            }

            Organization newParent = orgMapper.findById(req.parentId())
                    .orElseThrow(() -> new OrganizationNotFoundException(req.parentId()));

            newDepth = newParent.getDepth() + 1;
            if (newDepth > MAX_DEPTH) {
                throw new DepthExceededException(newDepth);
            }
            newPath = newParent.getPath() + id + "/";

            // 자손 path 일괄 갱신
            orgMapper.updateDescendantPaths(oldPath, newPath);
        }

        OrganizationStatus newStatus = req.status() != null
                ? OrganizationStatus.valueOf(req.status())
                : org.getStatus();

        org.setName(req.name() != null ? req.name() : org.getName());
        org.setDescription(req.description() != null ? req.description() : org.getDescription());
        org.setParentId(req.parentId() != null ? req.parentId() : org.getParentId());
        org.setDepth(newDepth);
        org.setPath(newPath);
        org.setSortOrder(req.sortOrder() != null ? req.sortOrder() : org.getSortOrder());
        org.setStatus(newStatus);

        orgMapper.update(org);
        insertHistory(org, "UPDATE", actorId);
        return toDetail(org);
    }

    @Override
    @Transactional
    @AuditLog(action = "DELETE", entityType = "Organization", severity = "WARN")
    public void delete(long id, long actorId) {
        Organization org = orgMapper.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));

        if (orgMapper.countActiveChildren(id) > 0) {
            throw new OrganizationHasChildrenException(id);
        }
        if (orgMapper.countAttachedUsers(id) > 0) {
            throw new OrganizationHasUsersException(id);
        }

        Instant now = Instant.now();
        orgMapper.softDelete(id, now);
        org.setStatus(OrganizationStatus.DELETED);
        org.setDeletedAt(now);
        insertHistory(org, "DELETE", actorId);
    }

    @Override
    public List<OrganizationHistoryEntry> getHistory(long orgId) {
        orgMapper.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException(orgId));

        return histMapper.findByOrgId(orgId).stream()
                .map(this::toHistoryEntry)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @AuditLog(action = "UPDATE", entityType = "User", severity = "INFO")
    public void assignUser(long userId, Long organizationId, long actorId) {
        userMapper.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (organizationId != null) {
            orgMapper.findById(organizationId)
                    .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
        }

        userMapper.updateOrganization(userId, organizationId, Instant.now());
    }

    // ─── 내부 헬퍼 메서드 ────────────────────────────────────────────

    /**
     * Organization 리스트에서 재귀 트리 구성 (단일 SELECT 후 Java 메모리 빌드).
     */
    private List<OrganizationTreeNode> buildTree(List<Organization> all, Long parentId) {
        return all.stream()
                .filter(o -> (parentId == null
                        ? o.getParentId() == null
                        : parentId.equals(o.getParentId())))
                .sorted((a, b) -> {
                    int s = Integer.compare(a.getSortOrder(), b.getSortOrder());
                    return s != 0 ? s : a.getName().compareTo(b.getName());
                })
                .map(o -> new OrganizationTreeNode(
                        o.getId(),
                        o.getCode(),
                        o.getName(),
                        o.getDepth(),
                        o.getSortOrder(),
                        o.getStatus().name(),
                        buildTree(all, o.getId())
                ))
                .collect(Collectors.toList());
    }

    /**
     * 대상 노드 ID가 지정 경로(path)의 자손인지 확인.
     *
     * <p>path가 '/{ancestorId}/...' 형식이므로 targetId가 path에 포함되면 자손.
     */
    private boolean isDescendant(long targetId, String ancestorPath) {
        return ancestorPath.contains("/" + targetId + "/");
    }

    private void insertHistory(Organization org, String summary, long actorId) {
        int nextVersion = histMapper.findMaxVersion(org.getId()) + 1;
        String snapshot = toJsonSnapshot(org);
        OrganizationHistory hist = OrganizationHistory.builder()
                .orgId(org.getId())
                .version(nextVersion)
                .snapshot(snapshot)
                .changedBy(actorId)
                .changeSummary(summary)
                .build();
        histMapper.insert(hist);
    }

    private String toJsonSnapshot(Organization org) {
        try {
            return objectMapper.writeValueAsString(org);
        } catch (Exception e) {
            log.warn("조직 스냅샷 직렬화 실패: orgId={}", org.getId(), e);
            return "{}";
        }
    }

    private OrganizationSummary toSummary(Organization o) {
        return new OrganizationSummary(
                o.getId(), o.getCode(), o.getName(), o.getParentId(),
                o.getDepth(), o.getSortOrder(), o.getStatus().name());
    }

    private OrganizationDetail toDetail(Organization o) {
        return new OrganizationDetail(
                o.getId(), o.getCode(), o.getName(), o.getDescription(),
                o.getParentId(), o.getDepth(), o.getPath(), o.getSortOrder(),
                o.getStatus().name(), o.getCreatedAt(), o.getUpdatedAt());
    }

    private OrganizationHistoryEntry toHistoryEntry(OrganizationHistory h) {
        Map<String, Object> snapshotMap;
        try {
            snapshotMap = objectMapper.readValue(h.getSnapshot(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("이력 스냅샷 역직렬화 실패: histId={}", h.getId(), e);
            snapshotMap = Map.of();
        }
        return new OrganizationHistoryEntry(
                h.getId(), h.getOrgId(), h.getVersion(), snapshotMap,
                h.getChangedBy(), h.getChangedAt(), h.getChangeSummary());
    }
}
