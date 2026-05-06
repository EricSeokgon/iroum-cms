package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.SavedViewRequest;
import kr.co.ircp.cms.domain.dashboard.dto.SavedViewResponse;
import kr.co.ircp.cms.domain.dashboard.entity.SavedView;
import kr.co.ircp.cms.domain.dashboard.exception.SavedViewNotFoundException;
import kr.co.ircp.cms.domain.dashboard.repository.SavedViewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SavedViewService 단위 테스트.
 * REQ-VIZ-004 (저장된 필터/뷰)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SavedViewService — 저장된 뷰 CRUD + apply (REQ-VIZ-004)")
class SavedViewServiceTest {

    @Mock private SavedViewMapper viewMapper;

    private SavedViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SavedViewServiceImpl(viewMapper);
    }

    private SavedView sample(Long id, Long ownerId, Long dashboardId, String name) {
        return SavedView.builder()
                .id(id).ownerId(ownerId).dashboardId(dashboardId).name(name)
                .filterState("{\"period\":\"7d\"}")
                .isDefault(false).isShared(false)
                .sharedWith(List.of())
                .createdAt(Instant.now()).lastUsedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("create — 저장된 뷰 신규 등록")
    void create_success() {
        Answer<Void> assignId = (InvocationOnMock inv) -> {
            SavedView v = inv.getArgument(0);
            v.setId(7L);
            return null;
        };
        org.mockito.Mockito.doAnswer(assignId).when(viewMapper).insert(any());

        SavedViewRequest req = new SavedViewRequest(
                100L, "최근 7일", "desc",
                "{\"period\":\"7d\"}", false, false, List.of());

        SavedViewResponse resp = service.create(1L, req);

        assertThat(resp.id()).isEqualTo(7L);
        verify(viewMapper).insert(any());
    }

    @Test
    @DisplayName("apply — last_used_at 갱신 + 응답 반환")
    void apply_touchesLastUsedAt() {
        when(viewMapper.findById(7L)).thenReturn(Optional.of(sample(7L, 1L, 100L, "v1")));

        SavedViewResponse resp = service.apply(7L, 1L);

        assertThat(resp.id()).isEqualTo(7L);
        verify(viewMapper, times(1)).touchLastUsedAt(7L);
    }

    @Test
    @DisplayName("apply — 미존재 시 SavedViewNotFoundException")
    void apply_notFound() {
        when(viewMapper.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.apply(99L, 1L))
                .isInstanceOf(SavedViewNotFoundException.class);
    }

    @Test
    @DisplayName("delete — 본인 소유가 아니면 SecurityException")
    void delete_notOwner_throws() {
        when(viewMapper.findById(7L)).thenReturn(Optional.of(sample(7L, 99L, 100L, "v1")));
        assertThatThrownBy(() -> service.delete(7L, 1L))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("listForUser — 본인 + dashboard 조건 매칭")
    void list_returnsOwnerViews() {
        when(viewMapper.findByOwnerAndDashboard(1L, 100L))
                .thenReturn(List.of(sample(7L, 1L, 100L, "v1"), sample(8L, 1L, 100L, "v2")));

        List<SavedViewResponse> resp = service.listForUser(1L, 100L);

        assertThat(resp).hasSize(2);
    }
}
