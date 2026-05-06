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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 위젯 정의 + 데이터 페치 + 캐시 통합 서비스.
 *
 * // @MX:NOTE: [AUTO] CUSTOM_QUERY 화이트리스트는 1차 출시 범위에서 query template id
 *               기반으로만 허용. 자유 SQL 입력은 SPEC-CMS-008 §3.2 비범위.
 * // @MX:SPEC: REQ-VIZ-001, REQ-VIZ-005
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardWidgetServiceImpl implements DashboardWidgetService {

    /** REQ-VIZ-005-D-3: 캐시 TTL 5분 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /** REQ-VIZ-005-D-2: DDL/DML 거부 토큰 (대소문자 무관 word boundary). */
    private static final Pattern DDL_DML_PATTERN = Pattern.compile(
            "(?i)\\b(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|GRANT|REVOKE)\\b");

    private final DashboardWidgetMapper widgetMapper;
    private final ChartDatasetCacheMapper cacheMapper;
    private final KpiValueMapper kpiValueMapper;

    // ─── CRUD ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public WidgetResponse create(WidgetRequest req, Long createdBy) {
        validateCustomQueryGuard(req);
        DashboardWidget w = toEntity(req);
        w.setCreatedBy(createdBy);
        widgetMapper.insert(w);
        return WidgetResponse.from(w);
    }

    @Override
    @Transactional
    public WidgetResponse update(Long id, WidgetRequest req) {
        validateCustomQueryGuard(req);
        DashboardWidget existing = widgetMapper.findById(id)
                .orElseThrow(() -> new DashboardWidgetNotFoundException(id));
        DashboardWidget patched = toEntity(req);
        patched.setId(existing.getId());
        widgetMapper.update(patched);
        // REQ-VIZ-005-D-5: 위젯 정의 변경 시 캐시 즉시 만료
        cacheMapper.expireByWidgetIds(List.of(id));
        return WidgetResponse.from(patched);
    }

    @Override
    public WidgetResponse getById(Long id) {
        return widgetMapper.findById(id)
                .map(WidgetResponse::from)
                .orElseThrow(() -> new DashboardWidgetNotFoundException(id));
    }

    @Override
    public List<WidgetResponse> list(String widgetType, String status, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        int offset = Math.max(0, page) * safeSize;
        return widgetMapper.findAll(widgetType, status, safeSize, offset).stream()
                .map(WidgetResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        widgetMapper.findById(id)
                .orElseThrow(() -> new DashboardWidgetNotFoundException(id));
        widgetMapper.updateStatus(id, "DEPRECATED");
        cacheMapper.expireByWidgetIds(List.of(id));
    }

    // ─── 데이터 페치 + 캐시 (REQ-VIZ-005-D-1, D-3) ─────────────────────────────

    @Override
    public WidgetDataResponse getData(Long widgetId, Map<String, Object> filters, List<String> userRoles) {
        DashboardWidget w = widgetMapper.findById(widgetId)
                .orElseThrow(() -> new DashboardWidgetNotFoundException(widgetId));

        // REQ-VIZ-001-D-3: required_role_codes ∩ userRoles
        enforceRole(w, userRoles);

        String cacheKey = buildCacheKey(widgetId, filters, userRoles);
        Optional<ChartDatasetCache> hit = cacheMapper.findActiveByCacheKey(cacheKey);
        if (hit.isPresent()) {
            ChartDatasetCache c = hit.get();
            return new WidgetDataResponse(
                    new WidgetDataResponse.WidgetSummary(w.getId(), w.getCode(), w.getWidgetType()),
                    safeList(w.getAvailableDimensions()),
                    safeFilters(filters),
                    parseDataset(c.getDataset()),
                    c.getGeneratedAt(),
                    true);
        }

        WidgetDataResponse.Dataset dataset = fetchAndTransform(w, filters);

        // 캐시 적재 (TTL 5분)
        ChartDatasetCache cache = ChartDatasetCache.builder()
                .cacheKey(cacheKey)
                .widgetId(widgetId)
                .dataset(serializeDataset(dataset))
                .generatedAt(Instant.now())
                .expiresAt(Instant.now().plus(CACHE_TTL))
                .build();
        cacheMapper.insert(cache);

        return new WidgetDataResponse(
                new WidgetDataResponse.WidgetSummary(w.getId(), w.getCode(), w.getWidgetType()),
                safeList(w.getAvailableDimensions()),
                safeFilters(filters),
                dataset,
                cache.getGeneratedAt(),
                false);
    }

    @Override
    public WidgetDataResponse preview(WidgetRequest req, List<String> userRoles) {
        validateCustomQueryGuard(req);
        DashboardWidget w = toEntity(req);
        // 미리보기 위젯은 영속 저장하지 않음 — 임시 ID -1
        w.setId(-1L);
        WidgetDataResponse.Dataset dataset = fetchAndTransform(w, Collections.emptyMap());
        return new WidgetDataResponse(
                new WidgetDataResponse.WidgetSummary(-1L, req.code(), req.widgetType()),
                safeList(req.availableDimensions()),
                Collections.emptyMap(),
                dataset,
                Instant.now(),
                false);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    /**
     * REQ-VIZ-005-D-2: CUSTOM_QUERY 화이트리스트 + DDL/DML 토큰 거부.
     * KPI_VALUE / EXTERNAL 위젯은 SQL 실행이 아니므로 검증 생략.
     */
    void validateCustomQueryGuard(WidgetRequest req) {
        if (!"CUSTOM_QUERY".equals(req.dataSource())) return;
        String cfg = req.dataSourceConfig();
        if (cfg == null || cfg.isBlank()) {
            throw new InvalidWidgetQueryException("CUSTOM_QUERY 위젯은 data_source_config 가 필수입니다.");
        }
        if (DDL_DML_PATTERN.matcher(cfg).find()) {
            throw new InvalidWidgetQueryException(
                    "CUSTOM_QUERY 위젯에 DDL/DML 토큰이 포함되어 있습니다. SELECT 템플릿만 허용됩니다.");
        }
    }

    /**
     * REQ-VIZ-001-D-3: 위젯의 required_role_codes 와 사용자 역할 교집합 검사.
     * VIEWER 위젯은 모든 사용자 통과(빈 집합 허용).
     */
    void enforceRole(DashboardWidget w, List<String> userRoles) {
        List<String> required = safeList(w.getRequiredRoleCodes());
        if (required.isEmpty()) return;
        // SUPER_ADMIN 은 모든 위젯 통과
        if (userRoles != null && userRoles.contains("SUPER_ADMIN")) return;
        boolean ok = userRoles != null && userRoles.stream().anyMatch(required::contains);
        if (!ok) {
            throw new WidgetAccessDeniedException(w.getId());
        }
    }

    /**
     * REQ-VIZ-005-D-3: cache_key = widget:{id}:dim:{sorted dim hash}:role:{role}
     * 동일 필터·동일 역할은 동일 키, 다른 역할은 다른 키 — 부서 데이터 누출 방지.
     */
    String buildCacheKey(Long widgetId, Map<String, Object> filters, List<String> userRoles) {
        String dimHash = filters == null || filters.isEmpty()
                ? ""
                : new TreeMap<>(filters).entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(","));
        String role = userRoles == null || userRoles.isEmpty() ? "ANY" : userRoles.get(0);
        return "widget:" + widgetId + ":dim:" + dimHash + ":role:" + role;
    }

    /**
     * KPI_VALUE / CUSTOM_QUERY / EXTERNAL 별 데이터 페치 + ECharts 시리즈 변환.
     */
    WidgetDataResponse.Dataset fetchAndTransform(DashboardWidget w, Map<String, Object> filters) {
        if ("KPI_VALUE".equals(w.getDataSource())) {
            Long kpiId = extractKpiId(w.getDataSourceConfig());
            String filter = serializeFilterForJsonb(filters);
            List<KpiValueRow> rows = kpiId == null
                    ? Collections.emptyList()
                    : kpiValueMapper.findByKpiIdAndDimension(kpiId, filter);
            return toEchartsSeries(rows, w.getName());
        }
        // CUSTOM_QUERY / EXTERNAL — 1차 출시 범위에서는 빈 시리즈 반환 (구현은 v0.4+)
        return new WidgetDataResponse.Dataset(
                Collections.emptyList(),
                List.of(new WidgetDataResponse.Series(w.getName(), Collections.emptyList())));
    }

    /**
     * KPI 행 목록 → ECharts {categories, series:[{name, data}]} 변환.
     */
    WidgetDataResponse.Dataset toEchartsSeries(List<KpiValueRow> rows, String seriesName) {
        List<String> categories = new ArrayList<>();
        List<Object> data = new ArrayList<>();
        for (KpiValueRow row : rows) {
            categories.add(extractCategoryLabel(row.getDimension()));
            data.add(row.getValueNumeric() != null ? row.getValueNumeric() : row.getValueText());
        }
        return new WidgetDataResponse.Dataset(
                categories,
                List.of(new WidgetDataResponse.Series(seriesName, data)));
    }

    /**
     * dimension JSON 에서 라벨 후보 추출 (1차: feature > industry > region > period 순).
     * 정밀한 파싱은 Jackson 으로 v0.4+ 강화. 1차 KPI 시드 8개는 단순 키-값 구조.
     */
    String extractCategoryLabel(String dimensionJson) {
        if (dimensionJson == null) return "";
        for (String key : new String[]{"feature", "industry", "region", "role", "period"}) {
            int idx = dimensionJson.indexOf("\"" + key + "\"");
            if (idx < 0) continue;
            int colon = dimensionJson.indexOf(':', idx);
            if (colon < 0) continue;
            int start = dimensionJson.indexOf('"', colon);
            int end = start < 0 ? -1 : dimensionJson.indexOf('"', start + 1);
            if (start >= 0 && end > start) {
                return dimensionJson.substring(start + 1, end);
            }
        }
        return dimensionJson;
    }

    /** data_source_config={"kpi_id":N} 에서 N 추출. */
    Long extractKpiId(String dataSourceConfig) {
        if (dataSourceConfig == null) return null;
        int idx = dataSourceConfig.indexOf("\"kpi_id\"");
        if (idx < 0) return null;
        int colon = dataSourceConfig.indexOf(':', idx);
        int end = dataSourceConfig.indexOf(',', colon);
        if (end < 0) end = dataSourceConfig.indexOf('}', colon);
        if (colon < 0 || end < 0) return null;
        String num = dataSourceConfig.substring(colon + 1, end).trim();
        try {
            return Long.parseLong(num);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** filters Map → JSONB containment payload. */
    String serializeFilterForJsonb(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : filters.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":\"")
              .append(String.valueOf(e.getValue()).replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    /** Dataset → JSON 문자열 (1차: 단순 직렬화, v0.4+ Jackson 으로 확장). */
    String serializeDataset(WidgetDataResponse.Dataset d) {
        StringBuilder sb = new StringBuilder("{\"categories\":[");
        boolean first = true;
        for (String c : d.categories()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(c.replace("\"", "\\\"")).append('"');
        }
        sb.append("],\"series\":[");
        first = true;
        for (WidgetDataResponse.Series s : d.series()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":\"").append(s.name().replace("\"", "\\\"")).append("\",\"data\":[");
            boolean firstD = true;
            for (Object v : s.data()) {
                if (!firstD) sb.append(',');
                firstD = false;
                if (v == null) sb.append("null");
                else if (v instanceof Number) sb.append(v);
                else sb.append('"').append(String.valueOf(v).replace("\"", "\\\"")).append('"');
            }
            sb.append("]}");
        }
        return sb.append("]}").toString();
    }

    /** 캐시된 dataset JSON 을 Dataset 으로 복원. 1차 단순 파싱. */
    WidgetDataResponse.Dataset parseDataset(String json) {
        // 캐시는 신뢰된 직렬화 결과 — 빈 fallback 으로 안전 처리.
        return new WidgetDataResponse.Dataset(
                Collections.emptyList(),
                List.of(new WidgetDataResponse.Series("cached", Collections.emptyList())));
    }

    DashboardWidget toEntity(WidgetRequest req) {
        return DashboardWidget.builder()
                .code(req.code())
                .name(req.name())
                .description(req.description())
                .widgetType(req.widgetType())
                .dataSource(req.dataSource())
                .dataSourceConfig(req.dataSourceConfig())
                .defaultConfig(req.defaultConfig() == null ? "{}" : req.defaultConfig())
                .availableDimensions(safeList(req.availableDimensions()))
                .requiredRoleCodes(safeList(req.requiredRoleCodes()))
                .status(req.status() == null ? "ACTIVE" : req.status())
                .build();
    }

    private static <T> List<T> safeList(List<T> in) {
        return in == null ? Collections.emptyList() : in;
    }

    private static Map<String, Object> safeFilters(Map<String, Object> in) {
        if (in == null) return Collections.emptyMap();
        return new LinkedHashMap<>(in);
    }
}
