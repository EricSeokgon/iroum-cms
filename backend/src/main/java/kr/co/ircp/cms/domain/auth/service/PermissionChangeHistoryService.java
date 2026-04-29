package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PermissionChangeEntry;
import kr.co.ircp.cms.domain.auth.entity.PermissionChangeHistory;
import kr.co.ircp.cms.domain.auth.entity.PermissionChangeType;
import kr.co.ircp.cms.domain.auth.repository.PermissionChangeHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 권한 변경 이력 서비스.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-016 §13.A — 권한 변경 사실을 APPEND-ONLY 테이블에 비동기 적재하고 페이징 조회를 제공한다.
 *
 * <ul>
 *   <li>severity 결정: SUPER_ADMIN 역할 부여/회수 → CRITICAL, 그 외 → INFO</li>
 *   <li>비동기 적재: auditExecutor 재사용 (@Async("auditExecutor"))</li>
 *   <li>MDC에서 trace_id, ip_address 추출하여 함께 저장</li>
 * </ul>
 */
// @MX:ANCHOR: [AUTO] PermissionChangeHistoryService — 권한 변경 이력 도메인 핵심 서비스
// @MX:REASON: UserServiceImpl, RoleService, PermissionChangeController 등 fan_in >= 3에서 호출; REQ-AUTH-016 컴플라이언스 엔트리포인트
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionChangeHistoryService {

    private static final String SEVERITY_CRITICAL = "CRITICAL";
    private static final String SEVERITY_INFO = "INFO";
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final PermissionChangeHistoryMapper mapper;

    // ─────────────────────────────────────────────────────────────────
    // 이력 적재 메서드 (비동기)
    // ─────────────────────────────────────────────────────────────────

    /**
     * 사용자에게 역할이 부여될 때 이력을 적재한다.
     *
     * @param targetUserId 대상 사용자 ID
     * @param roleCode     부여된 역할 코드
     * @param actorId      수행자 ID
     * @param reason       변경 사유
     */
    // @MX:WARN: [AUTO] recordRoleAssignment — @Async 비동기 실행; 트랜잭션 전파 REQUIRES_NEW
    // @MX:REASON: 호출 스레드 트랜잭션과 분리되어야 비즈니스 롤백 시에도 이력이 보존됨
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRoleAssignment(long targetUserId, String roleCode, long actorId, String reason) {
        String severity = isCriticalRole(roleCode) ? SEVERITY_CRITICAL : SEVERITY_INFO;
        persist(PermissionChangeType.ROLE_ASSIGN, targetUserId, roleCode, roleCode, actorId, severity, reason);
    }

    /**
     * 사용자에서 역할이 회수될 때 이력을 적재한다.
     *
     * @param targetUserId 대상 사용자 ID
     * @param roleCode     회수된 역할 코드
     * @param actorId      수행자 ID
     * @param reason       변경 사유
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRoleUnassignment(long targetUserId, String roleCode, long actorId, String reason) {
        String severity = isCriticalRole(roleCode) ? SEVERITY_CRITICAL : SEVERITY_INFO;
        persist(PermissionChangeType.ROLE_UNASSIGN, targetUserId, roleCode, roleCode, actorId, severity, reason);
    }

    /**
     * 역할에 권한이 부여될 때 이력을 적재한다.
     *
     * @param roleCode       대상 역할 코드
     * @param permissionCode 부여된 권한 코드
     * @param actorId        수행자 ID
     * @param reason         변경 사유
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPermissionGrant(String roleCode, String permissionCode, long actorId, String reason) {
        persist(PermissionChangeType.ROLE_PERMISSION_GRANT, null, roleCode, permissionCode, actorId, SEVERITY_INFO, reason);
    }

    /**
     * 역할에서 권한이 회수될 때 이력을 적재한다.
     *
     * @param roleCode       대상 역할 코드
     * @param permissionCode 회수된 권한 코드
     * @param actorId        수행자 ID
     * @param reason         변경 사유
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPermissionRevoke(String roleCode, String permissionCode, long actorId, String reason) {
        persist(PermissionChangeType.ROLE_PERMISSION_REVOKE, null, roleCode, permissionCode, actorId, SEVERITY_INFO, reason);
    }

    // ─────────────────────────────────────────────────────────────────
    // 조회 메서드
    // ─────────────────────────────────────────────────────────────────

    /**
     * 전체 권한 변경 이력 페이징 조회 (관리자용).
     *
     * @param page         페이지 번호 (0-based)
     * @param size         페이지 크기
     * @param sort         정렬 (changedAt,desc | changedAt,asc)
     * @param targetUserId 대상 사용자 필터 (null 시 전체)
     * @param changeType   변경 유형 필터 (null 시 전체)
     * @param changedBy    수행자 필터 (null 시 전체)
     * @param from         시작 시각 필터 (null 시 전체)
     * @param to           종료 시각 필터 (null 시 전체)
     * @return 페이징된 이력 목록
     */
    @Transactional(readOnly = true)
    public PageResponse<PermissionChangeEntry> findPage(
            int page, int size, String sort,
            Long targetUserId, String changeType, Long changedBy,
            Instant from, Instant to) {

        int offset = page * size;
        List<PermissionChangeEntry> content = mapper.findPage(
                offset, size, targetUserId, changeType, changedBy, from, to, sort);
        long total = mapper.countAll(targetUserId, changeType, changedBy, from, to);
        return PageResponse.of(content, page, size, total);
    }

    /**
     * 특정 사용자의 권한 변경 이력 페이징 조회.
     *
     * @param userId 대상 사용자 ID
     * @param page   페이지 번호 (0-based)
     * @param size   페이지 크기
     * @return 페이징된 이력 목록
     */
    @Transactional(readOnly = true)
    public PageResponse<PermissionChangeEntry> findByUser(long userId, int page, int size) {
        int offset = page * size;
        List<PermissionChangeEntry> content = mapper.findByTargetUser(userId, offset, size);
        long total = mapper.countByTargetUser(userId);
        return PageResponse.of(content, page, size, total);
    }

    // ─────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────────────

    private void persist(
            PermissionChangeType changeType,
            Long targetUserId,
            String targetRoleCode,
            String targetResource,
            Long changedBy,
            String severity,
            String reason) {

        String traceId = MDC.get("traceId");
        String actorIp = MDC.get("ipAddress");

        PermissionChangeHistory entry = PermissionChangeHistory.builder()
                .changeType(changeType)
                .targetUserId(targetUserId)
                .targetRoleCode(targetRoleCode)
                .targetResource(targetResource)
                .changedBy(changedBy)
                .changedAt(Instant.now())
                .severity(severity)
                .reason(reason)
                .actorIp(actorIp)
                .traceId(traceId)
                .build();

        try {
            mapper.insert(entry);
            if (SEVERITY_CRITICAL.equals(severity)) {
                log.warn("[REQ-AUTH-016] CRITICAL 권한 변경 이력: type={}, resource={}, changedBy={}, traceId={}",
                        changeType, targetResource, changedBy, traceId);
            }
        } catch (Exception e) {
            // 이력 적재 실패는 비즈니스 로직에 영향 주지 않음 (로그만 기록)
            log.error("[REQ-AUTH-016] 권한 변경 이력 적재 실패: type={}, resource={}", changeType, targetResource, e);
        }
    }

    private boolean isCriticalRole(String roleCode) {
        return SUPER_ADMIN_ROLE.equals(roleCode);
    }
}
