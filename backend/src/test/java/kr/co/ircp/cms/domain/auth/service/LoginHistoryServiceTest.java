package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.LoginHistoryEntry;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.repository.LoginHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * LoginHistoryService 단위 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-011 — 로그인 이력 페이징 조회 및 필터 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginHistoryService 단위 테스트")
class LoginHistoryServiceTest {

    @Mock
    private LoginHistoryMapper mapper;

    private LoginHistoryService service;

    @BeforeEach
    void setUp() {
        service = new LoginHistoryService(mapper);
    }

    // ──────────────────────────────────────────────────────────────
    // findPage
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findPage — 페이징 결과 반환 (offset = page * size)")
    void findPage_returnsPagedResults() {
        LoginHistoryEntry entry = sampleEntry(1L, 10L, true);
        when(mapper.findPage(eq(0), eq(20), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), anyString()))
                .thenReturn(List.of(entry));
        when(mapper.countAll(isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(1L);

        PageResponse<LoginHistoryEntry> result = service.findPage(0, 20, "createdAt,desc",
                null, null, null, null, null, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("findPage — userId 필터 Mapper로 전달")
    void findPage_filtersByUserId() {
        when(mapper.findPage(eq(0), eq(20), eq(10L), isNull(), isNull(), isNull(), isNull(), isNull(), anyString()))
                .thenReturn(List.of(sampleEntry(1L, 10L, true)));
        when(mapper.countAll(eq(10L), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(1L);

        PageResponse<LoginHistoryEntry> result = service.findPage(0, 20, "createdAt,desc",
                10L, null, null, null, null, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).userId()).isEqualTo(10L);
        verify(mapper).findPage(0, 20, 10L, null, null, null, null, null, "createdAt,desc");
    }

    @Test
    @DisplayName("findPage — success=false 필터 Mapper로 전달")
    void findPage_filtersBySuccess() {
        when(mapper.findPage(anyInt(), anyInt(), isNull(), isNull(), eq(false), isNull(), isNull(), isNull(), anyString()))
                .thenReturn(List.of(sampleEntry(2L, 5L, false)));
        when(mapper.countAll(isNull(), isNull(), eq(false), isNull(), isNull(), isNull()))
                .thenReturn(1L);

        PageResponse<LoginHistoryEntry> result = service.findPage(0, 20, "createdAt,desc",
                null, null, false, null, null, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).success()).isFalse();
    }

    @Test
    @DisplayName("findPage — 허용되지 않는 sort 값도 Mapper에 그대로 전달 (XML 화이트리스트로 폴백)")
    void findPage_appliesSortWhitelist_unknownSortFallsToDefault() {
        // 허용되지 않는 sort 값 → XML에서 createdAt DESC로 폴백됨 (Java 레이어 제한 없음)
        when(mapper.findPage(eq(0), eq(20), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq("invalid,column")))
                .thenReturn(List.of());
        when(mapper.countAll(isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(0L);

        PageResponse<LoginHistoryEntry> result = service.findPage(0, 20, "invalid,column",
                null, null, null, null, null, null);

        // Service 레이어는 그대로 통과; SQL 인젝션 방지는 XML 화이트리스트에서 처리
        assertThat(result.content()).isEmpty();
        verify(mapper).findPage(0, 20, null, null, null, null, null, null, "invalid,column");
    }

    @Test
    @DisplayName("findPage — page=1이면 offset=10으로 계산 (page * size)")
    void findPage_calculatesOffsetCorrectly() {
        when(mapper.findPage(eq(10), eq(10), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), anyString()))
                .thenReturn(List.of());
        when(mapper.countAll(isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(0L);

        service.findPage(1, 10, "createdAt,desc", null, null, null, null, null, null);

        verify(mapper).findPage(10, 10, null, null, null, null, null, null, "createdAt,desc");
    }

    // ──────────────────────────────────────────────────────────────
    // findByUserId
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByUserId — 해당 사용자 이력만 반환")
    void findByUserId_returnsUserOnly() {
        when(mapper.findByUserId(eq(7L), eq(0), eq(20)))
                .thenReturn(List.of(sampleEntry(3L, 7L, true), sampleEntry(4L, 7L, false)));
        when(mapper.countByUserId(7L)).thenReturn(2L);

        PageResponse<LoginHistoryEntry> result = service.findByUserId(7L, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).allMatch(e -> e.userId() == 7L);
        assertThat(result.totalElements()).isEqualTo(2L);
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    private LoginHistoryEntry sampleEntry(long id, Long userId, boolean success) {
        return new LoginHistoryEntry(
                id, userId, "testuser",
                "127.0.0.1", "Mozilla/5.0",
                success, success ? null : "INVALID_PASSWORD",
                Instant.now()
        );
    }
}
