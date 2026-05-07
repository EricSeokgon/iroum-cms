package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.governance.entity.RetentionPolicy;
import kr.co.ircp.cms.domain.governance.repository.RetentionPolicyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RetentionPolicyService GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-009 REQ-GOV-006 — target_table 기준 보존 정책 조회·등록·수정.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RetentionPolicyService GREEN 테스트 (REQ-GOV-006)")
class RetentionPolicyServiceTest {

    @Mock
    private RetentionPolicyMapper mapper;

    private RetentionPolicyService service;

    @BeforeEach
    void setUp() {
        service = new RetentionPolicyService(mapper);
    }

    // ─── 공통 스텁 빌더 ─────────────────────────────────────────────────────

    private RetentionPolicy stubPolicy(long id, String table, String type) {
        return RetentionPolicy.builder()
                .id(id)
                .targetTable(table)
                .policyType(type)
                .retentionMonths(36)
                .scheduleCron("0 0 3 * * ?")
                .status("ACTIVE")
                .description("정책 " + id)
                .build();
    }

    // ──────────────────────────────────────────────
    // findById
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findById — 존재하는 ID는 mapper.findById 결과 반환")
    void findById_existingId_returnsPolicy() {
        RetentionPolicy policy = stubPolicy(1L, "audit_log", "DELETE");
        when(mapper.findById(1L)).thenReturn(Optional.of(policy));

        Optional<RetentionPolicy> result = service.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        assertThat(result.get().getTargetTable()).isEqualTo("audit_log");
        assertThat(result.get().getPolicyType()).isEqualTo("DELETE");
        verify(mapper).findById(1L);
    }

    @Test
    @DisplayName("findById — 존재하지 않는 ID는 Optional.empty 반환")
    void findById_nonExistentId_returnsEmpty() {
        when(mapper.findById(999L)).thenReturn(Optional.empty());

        Optional<RetentionPolicy> result = service.findById(999L);

        assertThat(result).isEmpty();
        verify(mapper).findById(999L);
    }

    // ──────────────────────────────────────────────
    // findByTargetTable
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findByTargetTable — 매핑된 테이블 정책 반환")
    void findByTargetTable_existingTable_returnsPolicy() {
        RetentionPolicy policy = stubPolicy(2L, "user_login_history", "ARCHIVE");
        when(mapper.findByTargetTable("user_login_history")).thenReturn(Optional.of(policy));

        Optional<RetentionPolicy> result = service.findByTargetTable("user_login_history");

        assertThat(result).isPresent();
        assertThat(result.get().getTargetTable()).isEqualTo("user_login_history");
        assertThat(result.get().getPolicyType()).isEqualTo("ARCHIVE");
        verify(mapper).findByTargetTable("user_login_history");
    }

    @Test
    @DisplayName("findByTargetTable — 매핑되지 않은 테이블은 Optional.empty 반환")
    void findByTargetTable_unmappedTable_returnsEmpty() {
        when(mapper.findByTargetTable("unknown_table")).thenReturn(Optional.empty());

        Optional<RetentionPolicy> result = service.findByTargetTable("unknown_table");

        assertThat(result).isEmpty();
        verify(mapper).findByTargetTable("unknown_table");
    }

    // ──────────────────────────────────────────────
    // findAll
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findAll — 등록된 모든 정책 반환")
    void findAll_returnsAllPolicies() {
        List<RetentionPolicy> stubs = List.of(
                stubPolicy(1L, "audit_log", "DELETE"),
                stubPolicy(2L, "user_login_history", "ARCHIVE"),
                stubPolicy(3L, "users", "ANONYMIZE")
        );
        when(mapper.findAll()).thenReturn(stubs);

        List<RetentionPolicy> result = service.findAll();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(RetentionPolicy::getPolicyType)
                .containsExactly("DELETE", "ARCHIVE", "ANONYMIZE");
        verify(mapper).findAll();
    }

    @Test
    @DisplayName("findAll — 등록 정책이 없으면 빈 리스트 반환")
    void findAll_emptyResult_returnsEmptyList() {
        when(mapper.findAll()).thenReturn(List.of());

        List<RetentionPolicy> result = service.findAll();

        assertThat(result).isEmpty();
        verify(mapper).findAll();
    }

    // ──────────────────────────────────────────────
    // create
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("create — mapper.insert 호출 후 동일 객체 반환")
    void create_callsInsertAndReturnsPolicy() {
        RetentionPolicy newPolicy = stubPolicy(0L, "policy_match_log", "DELETE");

        RetentionPolicy result = service.create(newPolicy);

        ArgumentCaptor<RetentionPolicy> captor = ArgumentCaptor.forClass(RetentionPolicy.class);
        verify(mapper).insert(captor.capture());
        RetentionPolicy inserted = captor.getValue();
        assertThat(inserted.getTargetTable()).isEqualTo("policy_match_log");
        assertThat(inserted.getPolicyType()).isEqualTo("DELETE");
        assertThat(result).isSameAs(newPolicy);
    }

    // ──────────────────────────────────────────────
    // update
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("update — mapper.update 호출 후 동일 객체 반환")
    void update_callsUpdateAndReturnsPolicy() {
        RetentionPolicy modified = stubPolicy(1L, "audit_log", "ARCHIVE");

        RetentionPolicy result = service.update(modified);

        ArgumentCaptor<RetentionPolicy> captor = ArgumentCaptor.forClass(RetentionPolicy.class);
        verify(mapper).update(captor.capture());
        RetentionPolicy updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getPolicyType()).isEqualTo("ARCHIVE");
        assertThat(result).isSameAs(modified);
    }

    @Test
    @DisplayName("update — read 호출 없이 곧바로 mapper.update 위임 (find 미호출)")
    void update_doesNotCallFindBeforeUpdate() {
        RetentionPolicy modified = stubPolicy(5L, "content_view_log", "DELETE");

        service.update(modified);

        verify(mapper).update(modified);
        // find 류 메서드는 호출되지 않아야 함
        verify(mapper, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.any());
        verify(mapper, org.mockito.Mockito.never()).findAll();
    }
}
