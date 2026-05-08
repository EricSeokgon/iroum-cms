package kr.co.ircp.cms.domain.auth.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PersonalDataAccessEntry;
import kr.co.ircp.cms.domain.auth.entity.PersonalDataAccessLog;
import kr.co.ircp.cms.domain.auth.entity.PersonalDataAccessPurpose;
import kr.co.ircp.cms.domain.auth.repository.PersonalDataAccessLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PersonalDataAccessLogService 단위 테스트.
 *
 * <p>REQ-AUTH-018-D-1~4 — Mockito 기반 서비스 레이어 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalDataAccessLogService 단위 테스트")
class PersonalDataAccessLogServiceTest {

    @Mock
    private PersonalDataAccessLogMapper mapper;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private PersonalDataAccessLogService service;

    @BeforeEach
    void setUp() {
        service = new PersonalDataAccessLogServiceImpl(mapper, meterRegistry);
    }

    // ──────────────────────────────────────────────────────────────
    // record
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("record — PersonalDataAccessLog를 mapper.insert에 전달한다")
    void record_persistsLog() {
        // when
        service.record(10L, "SUPER_ADMIN", 20L,
                Set.of("email", "name"), PersonalDataAccessPurpose.BUSINESS_INQUIRY);

        // then
        ArgumentCaptor<PersonalDataAccessLog> captor = ArgumentCaptor.forClass(PersonalDataAccessLog.class);
        verify(mapper).insert(captor.capture());
        PersonalDataAccessLog captured = captor.getValue();
        assertThat(captured.getViewerId()).isEqualTo(10L);
        assertThat(captured.getTargetUserId()).isEqualTo(20L);
        assertThat(captured.getPurpose()).isEqualTo("BUSINESS_INQUIRY");
        assertThat(captured.getAccessedFields()).containsExactlyInAnyOrder("email", "name");
    }

    // ──────────────────────────────────────────────────────────────
    // findPage
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findPage — targetUserId 필터로 조회한다")
    void findPage_filtersByTargetUser() {
        List<PersonalDataAccessEntry> rows = List.of(sampleEntry());
        when(mapper.findPage(eq(0), eq(20), eq(5L), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(rows);
        when(mapper.countAll(eq(5L), isNull(), isNull(), isNull(), isNull())).thenReturn(1L);

        PageResponse<PersonalDataAccessEntry> result =
                service.findPage(0, 20, "accessedAt,desc", 5L, null, null, null, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findPage — purpose 필터로 조회한다")
    void findPage_filtersByPurpose() {
        when(mapper.findPage(anyInt(), anyInt(), isNull(), isNull(), eq("AUDIT"), isNull(), isNull(), any()))
                .thenReturn(List.of());
        when(mapper.countAll(isNull(), isNull(), eq("AUDIT"), isNull(), isNull())).thenReturn(0L);

        PageResponse<PersonalDataAccessEntry> result =
                service.findPage(0, 20, "accessedAt,desc", null, null, "AUDIT", null, null);

        assertThat(result.content()).isEmpty();
        verify(mapper).findPage(0, 20, null, null, "AUDIT", null, null, "accessedAt,desc");
    }

    @Test
    @DisplayName("findPage — from/to 날짜 범위 필터로 조회한다")
    void findPage_filtersByDateRange() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-12-31T23:59:59Z");
        when(mapper.findPage(anyInt(), anyInt(), isNull(), isNull(), isNull(), eq(from), eq(to), any()))
                .thenReturn(List.of(sampleEntry()));
        when(mapper.countAll(isNull(), isNull(), isNull(), eq(from), eq(to))).thenReturn(1L);

        PageResponse<PersonalDataAccessEntry> result =
                service.findPage(0, 20, "accessedAt,desc", null, null, null, from, to);

        assertThat(result.totalElements()).isEqualTo(1L);
    }

    // ──────────────────────────────────────────────────────────────
    // findByTarget
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByTarget — 본인 이력 페이징 조회")
    void findByTarget_paginates() {
        List<PersonalDataAccessEntry> rows = List.of(sampleEntry());
        when(mapper.findByTarget(eq(99L), eq(0), eq(10))).thenReturn(rows);
        when(mapper.countByTarget(99L)).thenReturn(1L);

        PageResponse<PersonalDataAccessEntry> result = service.findByTarget(99L, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(0);
    }

    @Test
    @DisplayName("findByTarget — 존재하지 않는 사용자는 빈 목록 반환")
    void findByTarget_returnsEmpty_forNonExistentUser() {
        when(mapper.findByTarget(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countByTarget(anyLong())).thenReturn(0L);

        PageResponse<PersonalDataAccessEntry> result = service.findByTarget(9999L, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    private PersonalDataAccessEntry sampleEntry() {
        return new PersonalDataAccessEntry(
                1L, 10L, "admin", "SUPER_ADMIN",
                20L, "targetUser",
                List.of("email", "name"),
                "BUSINESS_INQUIRY",
                "127.0.0.1", "Mozilla/5.0",
                Instant.now()
        );
    }
}
