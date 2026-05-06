package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.WidgetDataResponse;
import kr.co.ircp.cms.domain.dashboard.dto.WidgetRequest;
import kr.co.ircp.cms.domain.dashboard.dto.WidgetResponse;
import kr.co.ircp.cms.domain.dashboard.entity.ChartDatasetCache;
import kr.co.ircp.cms.domain.dashboard.entity.DashboardWidget;
import kr.co.ircp.cms.domain.dashboard.entity.KpiValueRow;
import kr.co.ircp.cms.domain.dashboard.exception.DashboardWidgetNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.InvalidWidgetQueryException;
import kr.co.ircp.cms.domain.dashboard.exception.WidgetAccessDeniedException;
import kr.co.ircp.cms.domain.dashboard.repository.ChartDatasetCacheMapper;
import kr.co.ircp.cms.domain.dashboard.repository.DashboardWidgetMapper;
import kr.co.ircp.cms.domain.dashboard.repository.KpiValueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DashboardWidgetService 단위 테스트.
 * REQ-VIZ-001 (위젯 CRUD), REQ-VIZ-005 (데이터/캐시), REQ-VIZ-001-D-3 (권한)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardWidgetService — 위젯 CRUD + 데이터 페치 + 캐시 (REQ-VIZ-001/005)")
class DashboardWidgetServiceTest {

    @Mock private DashboardWidgetMapper widgetMapper;
    @Mock private ChartDatasetCacheMapper cacheMapper;
    @Mock private KpiValueMapper kpiValueMapper;

    private DashboardWidgetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardWidgetServiceImpl(widgetMapper, cacheMapper, kpiValueMapper);
    }

    private DashboardWidget kpiBarWidget() {
        return DashboardWidget.builder()
                .id(12L).code("PV_BY_FEATURE").name("기능별 PV")
                .widgetType("BAR_CHART").dataSource("KPI_VALUE")
                .dataSourceConfig("{\"kpi_id\":1}")
                .defaultConfig("{}")
                .availableDimensions(List.of("period", "feature"))
                .requiredRoleCodes(List.of("VIEWER"))
                .status("ACTIVE")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    // ──────────────────────────────────────────────
    // CRUD
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("create — KPI_VALUE 위젯 등록 성공")
    void create_kpiWidget_success() {
        WidgetRequest req = new WidgetRequest(
                "PV_BY_FEATURE", "기능별 PV", null,
                "BAR_CHART", "KPI_VALUE",
                "{\"kpi_id\":1}", "{}",
                List.of("period", "feature"), List.of("VIEWER"), "ACTIVE");

        WidgetResponse result = service.create(req, 1L);

        assertThat(result.code()).isEqualTo("PV_BY_FEATURE");
        verify(widgetMapper, times(1)).insert(any(DashboardWidget.class));
    }

    @Test
    @DisplayName("create — CUSTOM_QUERY 위젯에서 DDL 토큰 검출 시 InvalidWidgetQueryException")
    void create_customQueryWithDdlToken_throwsException() {
        WidgetRequest req = new WidgetRequest(
                "DDL_INJECTION", "DDL", null,
                "TABLE", "CUSTOM_QUERY",
                "{\"query\":\"DROP TABLE users\"}", "{}",
                List.of("period"), List.of("SUPER_ADMIN"), "ACTIVE");

        assertThatThrownBy(() -> service.create(req, 1L))
                .isInstanceOf(InvalidWidgetQueryException.class);
        verify(widgetMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create — CUSTOM_QUERY 위젯에서 INSERT/UPDATE/DELETE 토큰 검출 시 거부")
    void create_customQueryWithDml_throwsException() {
        for (String dml : List.of("INSERT INTO foo VALUES (1)",
                "UPDATE foo SET x = 1", "DELETE FROM foo", "TRUNCATE foo")) {
            WidgetRequest req = new WidgetRequest(
                    "X_" + dml.hashCode(), "x", null,
                    "TABLE", "CUSTOM_QUERY",
                    "{\"query\":\"" + dml + "\"}", "{}",
                    List.of("period"), List.of("SUPER_ADMIN"), "ACTIVE");
            assertThatThrownBy(() -> service.create(req, 1L))
                    .as("DML 토큰 거부: %s", dml)
                    .isInstanceOf(InvalidWidgetQueryException.class);
        }
    }

    @Test
    @DisplayName("update — 존재하지 않는 위젯이면 DashboardWidgetNotFoundException")
    void update_widgetNotFound_throwsException() {
        when(widgetMapper.findById(999L)).thenReturn(Optional.empty());
        WidgetRequest req = new WidgetRequest(
                "X", "X", null, "TABLE", "KPI_VALUE",
                "{\"kpi_id\":1}", "{}",
                List.of("period"), List.of("VIEWER"), "ACTIVE");

        assertThatThrownBy(() -> service.update(999L, req))
                .isInstanceOf(DashboardWidgetNotFoundException.class);
    }

    @Test
    @DisplayName("getById — 존재하지 않으면 DashboardWidgetNotFoundException")
    void getById_notFound_throwsException() {
        when(widgetMapper.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(DashboardWidgetNotFoundException.class);
    }

    @Test
    @DisplayName("delete — soft delete (status=DEPRECATED)")
    void delete_softDeletes() {
        when(widgetMapper.findById(12L)).thenReturn(Optional.of(kpiBarWidget()));
        when(widgetMapper.updateStatus(eq(12L), eq("DEPRECATED"))).thenReturn(1);

        service.delete(12L);

        verify(widgetMapper).updateStatus(12L, "DEPRECATED");
    }

    // ──────────────────────────────────────────────
    // 위젯 데이터 페치 + 캐시 (REQ-VIZ-005-D-1, D-3)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getData — 캐시 hit 시 DB 재계산 생략 (REQ-VIZ-005-D-3)")
    void getData_cacheHit_skipsDb() {
        DashboardWidget w = kpiBarWidget();
        when(widgetMapper.findById(12L)).thenReturn(Optional.of(w));
        ChartDatasetCache cached = ChartDatasetCache.builder()
                .id(1L).cacheKey("widget:12:dim::role:VIEWER")
                .widgetId(12L)
                .dataset("{\"categories\":[\"a\"],\"series\":[{\"name\":\"PV\",\"data\":[10]}]}")
                .generatedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(cacheMapper.findActiveByCacheKey(anyString())).thenReturn(Optional.of(cached));

        WidgetDataResponse resp = service.getData(12L, Map.of(), List.of("VIEWER"));

        assertThat(resp.cacheHit()).isTrue();
        assertThat(resp.widget().id()).isEqualTo(12L);
        verify(kpiValueMapper, never()).findByKpiIdAndDimension(anyLong(), anyString());
        verify(cacheMapper, never()).insert(any());
    }

    @Test
    @DisplayName("getData — 캐시 miss 시 KPI 조회 후 캐시 적재 (TTL 5분)")
    void getData_cacheMiss_queriesAndCaches() {
        DashboardWidget w = kpiBarWidget();
        when(widgetMapper.findById(12L)).thenReturn(Optional.of(w));
        when(cacheMapper.findActiveByCacheKey(anyString())).thenReturn(Optional.empty());
        when(kpiValueMapper.findByKpiIdAndDimension(eq(1L), anyString()))
                .thenReturn(List.of(
                        KpiValueRow.builder().kpiId(1L)
                                .dimension("{\"feature\":\"board\"}")
                                .valueNumeric(new BigDecimal("100"))
                                .calculatedAt(Instant.now()).build(),
                        KpiValueRow.builder().kpiId(1L)
                                .dimension("{\"feature\":\"policy\"}")
                                .valueNumeric(new BigDecimal("250"))
                                .calculatedAt(Instant.now()).build()
                ));

        WidgetDataResponse resp = service.getData(12L, Map.of("feature", "all"), List.of("VIEWER"));

        assertThat(resp.cacheHit()).isFalse();
        assertThat(resp.dataset().categories()).hasSize(2);
        assertThat(resp.dataset().series()).hasSize(1);
        assertThat(resp.dataset().series().get(0).data()).hasSize(2);
        verify(cacheMapper, times(1)).insert(any(ChartDatasetCache.class));
    }

    @Test
    @DisplayName("getData — required_role_codes 미일치 시 WidgetAccessDeniedException (REQ-VIZ-001-D-3)")
    void getData_roleMismatch_throwsAccessDenied() {
        DashboardWidget w = DashboardWidget.builder()
                .id(12L).code("ADMIN_ONLY").widgetType("BAR_CHART")
                .dataSource("KPI_VALUE")
                .dataSourceConfig("{\"kpi_id\":1}").defaultConfig("{}")
                .availableDimensions(List.of("period"))
                .requiredRoleCodes(List.of("SUPER_ADMIN"))
                .status("ACTIVE").build();
        when(widgetMapper.findById(12L)).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> service.getData(12L, Map.of(), List.of("VIEWER")))
                .isInstanceOf(WidgetAccessDeniedException.class);
    }

    @Test
    @DisplayName("getData — available_dimensions 가 응답에 포함된다 (REQ-VIZ-001-D-4)")
    void getData_responseIncludesAvailableDimensions() {
        DashboardWidget w = kpiBarWidget();
        when(widgetMapper.findById(12L)).thenReturn(Optional.of(w));
        when(cacheMapper.findActiveByCacheKey(anyString())).thenReturn(Optional.empty());
        when(kpiValueMapper.findByKpiIdAndDimension(eq(1L), anyString()))
                .thenReturn(List.of());

        WidgetDataResponse resp = service.getData(12L, Map.of(), List.of("VIEWER"));

        assertThat(resp.availableDimensions()).contains("period", "feature");
    }

    @Test
    @DisplayName("preview — 영속 저장 없이 임시 데이터만 반환 (REQ-VIZ-001-D-5)")
    void preview_noPersistence() {
        WidgetRequest req = new WidgetRequest(
                "PREVIEW_TMP", "Preview", null,
                "BAR_CHART", "KPI_VALUE",
                "{\"kpi_id\":1}", "{}",
                List.of("period"), List.of("VIEWER"), "ACTIVE");
        when(kpiValueMapper.findByKpiIdAndDimension(eq(1L), anyString()))
                .thenReturn(List.of());

        WidgetDataResponse resp = service.preview(req, List.of("VIEWER"));

        assertThat(resp).isNotNull();
        verify(widgetMapper, never()).insert(any());
        verify(cacheMapper, never()).insert(any());
    }

    @Test
    @DisplayName("getData — 위젯 status DEPRECATED 라도 데이터 조회는 허용한다(읽기)")
    void getData_deprecatedWidget_stillReadable() {
        DashboardWidget w = kpiBarWidget();
        w.setStatus("DEPRECATED");
        when(widgetMapper.findById(12L)).thenReturn(Optional.of(w));
        when(cacheMapper.findActiveByCacheKey(anyString())).thenReturn(Optional.empty());
        when(kpiValueMapper.findByKpiIdAndDimension(eq(1L), anyString())).thenReturn(List.of());

        WidgetDataResponse resp = service.getData(12L, Map.of(), List.of("VIEWER"));
        assertThat(resp).isNotNull();
    }
}
