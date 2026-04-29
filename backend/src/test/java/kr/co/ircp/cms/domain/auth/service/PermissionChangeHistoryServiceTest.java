package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PermissionChangeEntry;
import kr.co.ircp.cms.domain.auth.entity.PermissionChangeHistory;
import kr.co.ircp.cms.domain.auth.entity.PermissionChangeType;
import kr.co.ircp.cms.domain.auth.repository.PermissionChangeHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PermissionChangeHistoryService 단위 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-016 — 권한 변경 이력 적재 및 조회 검증.
 *
 * <p>주의: @Async 비동기 로직은 단위 테스트에서 동기적으로 실행됨 (Spring 컨텍스트 없음).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionChangeHistoryService 단위 테스트")
class PermissionChangeHistoryServiceTest {

    @Mock
    private PermissionChangeHistoryMapper mapper;

    private PermissionChangeHistoryService service;

    @BeforeEach
    void setUp() {
        service = new PermissionChangeHistoryService(mapper);
    }

    // ──────────────────────────────────────────────────────────────
    // recordRoleAssignment
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordRoleAssignment — ROLE_ASSIGN 타입으로 이력 적재")
    void recordRoleAssignment_persistsEntry() {
        service.recordRoleAssignment(10L, "VIEWER", 1L, "테스트 사유");

        ArgumentCaptor<PermissionChangeHistory> captor = ArgumentCaptor.forClass(PermissionChangeHistory.class);
        verify(mapper).insert(captor.capture());

        PermissionChangeHistory saved = captor.getValue();
        assertThat(saved.getChangeType()).isEqualTo(PermissionChangeType.ROLE_ASSIGN);
        assertThat(saved.getTargetUserId()).isEqualTo(10L);
        assertThat(saved.getTargetResource()).isEqualTo("VIEWER");
        assertThat(saved.getChangedBy()).isEqualTo(1L);
        assertThat(saved.getReason()).isEqualTo("테스트 사유");
    }

    // ──────────────────────────────────────────────────────────────
    // recordRoleUnassignment
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordRoleUnassignment — ROLE_UNASSIGN 타입으로 이력 적재")
    void recordRoleUnassignment_persistsEntry() {
        service.recordRoleUnassignment(10L, "EDITOR", 1L, "제거 사유");

        ArgumentCaptor<PermissionChangeHistory> captor = ArgumentCaptor.forClass(PermissionChangeHistory.class);
        verify(mapper).insert(captor.capture());

        PermissionChangeHistory saved = captor.getValue();
        assertThat(saved.getChangeType()).isEqualTo(PermissionChangeType.ROLE_UNASSIGN);
        assertThat(saved.getTargetUserId()).isEqualTo(10L);
        assertThat(saved.getTargetResource()).isEqualTo("EDITOR");
    }

    // ──────────────────────────────────────────────────────────────
    // recordPermissionGrant
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordPermissionGrant — ROLE_PERMISSION_GRANT 타입으로 이력 적재")
    void recordPermissionGrant_persistsEntry() {
        service.recordPermissionGrant("EDITOR", "AUDIT:READ", 1L, "권한 부여");

        ArgumentCaptor<PermissionChangeHistory> captor = ArgumentCaptor.forClass(PermissionChangeHistory.class);
        verify(mapper).insert(captor.capture());

        PermissionChangeHistory saved = captor.getValue();
        assertThat(saved.getChangeType()).isEqualTo(PermissionChangeType.ROLE_PERMISSION_GRANT);
        assertThat(saved.getTargetRoleCode()).isEqualTo("EDITOR");
        assertThat(saved.getTargetResource()).isEqualTo("AUDIT:READ");
        assertThat(saved.getTargetUserId()).isNull();
    }

    // ──────────────────────────────────────────────────────────────
    // SUPER_ADMIN severity=CRITICAL 정책
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SUPER_ADMIN 역할 부여 시 severity=CRITICAL (REQ-AUTH-016)")
    void recordRoleAssignment_superAdmin_severityCritical() {
        service.recordRoleAssignment(1L, "SUPER_ADMIN", 99L, "최고관리자 부여");

        ArgumentCaptor<PermissionChangeHistory> captor = ArgumentCaptor.forClass(PermissionChangeHistory.class);
        verify(mapper).insert(captor.capture());

        assertThat(captor.getValue().getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("SUPER_ADMIN 역할 회수 시 severity=CRITICAL (REQ-AUTH-016)")
    void recordRoleUnassignment_superAdmin_severityCritical() {
        service.recordRoleUnassignment(1L, "SUPER_ADMIN", 99L, "최고관리자 회수");

        ArgumentCaptor<PermissionChangeHistory> captor = ArgumentCaptor.forClass(PermissionChangeHistory.class);
        verify(mapper).insert(captor.capture());

        assertThat(captor.getValue().getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("일반 역할 변경 시 severity=INFO")
    void recordRoleAssignment_normalRole_severityInfo() {
        service.recordRoleAssignment(1L, "VIEWER", 99L, "일반 역할 부여");

        ArgumentCaptor<PermissionChangeHistory> captor = ArgumentCaptor.forClass(PermissionChangeHistory.class);
        verify(mapper).insert(captor.capture());

        assertThat(captor.getValue().getSeverity()).isEqualTo("INFO");
    }

    // ──────────────────────────────────────────────────────────────
    // findPage
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findPage — targetUserId 필터 적용하여 페이징 조회")
    void findPage_filtersByTargetUser() {
        PermissionChangeEntry entry = new PermissionChangeEntry(
                1L, 10L, "testuser", "ROLE_ASSIGN", "VIEWER", 1L, "admin", Instant.now(), null);
        when(mapper.findPage(eq(0), eq(20), eq(10L), isNull(), isNull(), isNull(), isNull(), anyString()))
                .thenReturn(List.of(entry));
        when(mapper.countAll(eq(10L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(1L);

        PageResponse<PermissionChangeEntry> result = service.findPage(0, 20, "changedAt,desc",
                10L, null, null, null, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.content().get(0).targetUserId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("findPage — changeType 필터 적용하여 페이징 조회")
    void findPage_filtersByChangeType() {
        when(mapper.findPage(anyInt(), anyInt(), isNull(), eq("ROLE_ASSIGN"), isNull(), isNull(), isNull(), anyString()))
                .thenReturn(List.of());
        when(mapper.countAll(isNull(), eq("ROLE_ASSIGN"), isNull(), isNull(), isNull()))
                .thenReturn(0L);

        PageResponse<PermissionChangeEntry> result = service.findPage(0, 20, "changedAt,desc",
                null, "ROLE_ASSIGN", null, null, null);

        assertThat(result.content()).isEmpty();
        verify(mapper).findPage(0, 20, null, "ROLE_ASSIGN", null, null, null, "changedAt,desc");
    }

    // ──────────────────────────────────────────────────────────────
    // findByUser
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByUser — 특정 사용자 이력 페이징 조회")
    void findByUser_paginates() {
        PermissionChangeEntry e1 = new PermissionChangeEntry(
                1L, 5L, "user5", "ROLE_ASSIGN", "VIEWER", 1L, "admin", Instant.now(), null);
        PermissionChangeEntry e2 = new PermissionChangeEntry(
                2L, 5L, "user5", "ROLE_UNASSIGN", "EDITOR", 1L, "admin", Instant.now(), null);

        when(mapper.findByTargetUser(eq(5L), eq(0), eq(20)))
                .thenReturn(List.of(e1, e2));
        when(mapper.countByTargetUser(5L)).thenReturn(2L);

        PageResponse<PermissionChangeEntry> result = service.findByUser(5L, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.page()).isEqualTo(0);
    }
}
