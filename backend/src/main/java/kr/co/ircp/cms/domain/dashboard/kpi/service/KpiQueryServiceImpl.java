package kr.co.ircp.cms.domain.dashboard.kpi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.dashboard.entity.ChartDatasetCache;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryRequest;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryResult;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiValueResponse;
import kr.co.ircp.cms.domain.dashboard.kpi.mapper.KpiQueryMapper;
import kr.co.ircp.cms.domain.dashboard.repository.ChartDatasetCacheMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * SPEC-CMS-KPI-001 Phase 2: KPI 조회 서비스 구현.
 *
 * <p>실제 스키마(V17) 기준:
 * <ul>
 *   <li>kpi_value 는 dimension JSONB 로 차원/기간을 인코딩(granularity 컬럼 없음).</li>
 *   <li>캐시는 chart_dataset_cache 재사용(widget_id=null, cache_key 접두사 'kpi:').</li>
 *   <li>전환율은 policy_match_stats_monthly 부재 시 PREPARING.</li>
 * </ul>
 *
 * // @MX:SPEC: SPEC-CMS-KPI-001 REQ-KPI-002 (AC-004/005/006/013/019/021/022)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KpiQueryServiceImpl implements KpiQueryService {

    /** REQ-KPI-004 AC-013: 캐시 TTL 5분. */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /** REQ-KPI-005 AC-021: DDL/DML 거부 토큰 (대소문자 무관 word boundary). */
    private static final Pattern DDL_DML_PATTERN = Pattern.compile(
            "(?i)\\b(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|GRANT|REVOKE|UNION)\\b");

    /** AC-006: 전환율 KPI 식별 코드. */
    private static final String CONVERSION_KPI_CODE = "policy_apply_conversion_rate";
    private static final String CONVERSION_KPI_NAME = "정책사업 신청 전환율";

    private final KpiQueryMapper kpiQueryMapper;
    private final ChartDatasetCacheMapper cacheMapper;
    private final ObjectMapper objectMapper;

    // @MX:ANCHOR: [AUTO] query — KPI 조회 진입점(캐시/상한/인젝션 방어 통합)
    // @MX:REASON: AdminKpiController 및 KpiExportService 가 본 메소드를 통해 데이터를 얻음 (fan_in >= 3 예상)
    @Override
    @Transactional
    public KpiQueryResult query(KpiQueryRequest request) {
        // AC-021: 인젝션 방어 — DB 접근 전에 검증하여 집계 쿼리 미실행 보장.
        rejectInjection(request.dimensionJson());
        rejectInjection(request.kpiCode());

        KpiQueryRequest req = request.normalize();
        Map<String, Object> filters = buildFilterMeta(req);

        String cacheKey = buildCacheKey(req);
        Optional<ChartDatasetCache> hit = cacheMapper.findActiveByCacheKey(cacheKey);
        if (hit.isPresent()) {
            return deserialize(hit.get().getDataset(), filters);
        }

        // AC-019: search 는 LIMIT(size<=1000)·OFFSET 적용, count 는 전체 행 수.
        List<KpiValueResponse> items = kpiQueryMapper.search(req);
        long total = kpiQueryMapper.count(req);
        boolean hasMore = total > items.size();
        KpiQueryResult result = new KpiQueryResult(items, total, hasMore, filters);

        cacheResult(cacheKey, result);
        return result;
    }

    @Override
    public KpiValueResponse conversionFunnel(String statMonth) {
        // AC-006: policy_match_stats_monthly 데이터 부재 → PREPARING (빈 값 미노출).
        int rows = kpiQueryMapper.countConversionStats(statMonth);
        if (rows == 0) {
            return KpiValueResponse.preparing(CONVERSION_KPI_CODE, CONVERSION_KPI_NAME);
        }
        // 데이터 존재 시 — Phase 2 범위에서는 READY 표식만 반환(상세 집계는 후속).
        // 실제 집계 산출은 SPEC-CMS-007 연동 후 확장.
        return new KpiValueResponse(CONVERSION_KPI_CODE, CONVERSION_KPI_NAME,
                "{\"month\":\"" + statMonth + "\"}", null, null, null, KpiValueResponse.READY);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** AC-021: DDL/DML 토큰 거부. null/blank 는 통과. */
    void rejectInjection(String value) {
        if (value == null || value.isBlank()) return;
        if (DDL_DML_PATTERN.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "KPI 조회 필터에 허용되지 않은 DDL/DML 토큰이 포함되어 있습니다.");
        }
    }

    /** AC-005: 적용된 필터 메타. */
    Map<String, Object> buildFilterMeta(KpiQueryRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kpiCode", req.kpiCode());
        m.put("fromDate", req.fromDate());
        m.put("toDate", req.toDate());
        m.put("dimensionJson", req.dimensionJson());
        m.put("granularity", req.granularity());
        m.put("page", req.page());
        m.put("size", req.size());
        return m;
    }

    /**
     * AC-013: cache_key = kpi:{code}:dim:{dimHash}:gran:{g}:page:{p}:size:{s}.
     * 동일 필터·동일 페이지는 동일 키 → 5분 내 재집계 회피.
     */
    String buildCacheKey(KpiQueryRequest req) {
        return "kpi:" + s(req.kpiCode())
                + ":from:" + s(req.fromDate())
                + ":to:" + s(req.toDate())
                + ":dim:" + s(req.dimensionJson())
                + ":gran:" + s(req.granularity())
                + ":page:" + req.page()
                + ":size:" + req.size();
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private void cacheResult(String cacheKey, KpiQueryResult result) {
        try {
            ChartDatasetCache cache = ChartDatasetCache.builder()
                    .cacheKey(cacheKey)
                    .widgetId(null)
                    .dataset(objectMapper.writeValueAsString(result))
                    .generatedAt(Instant.now())
                    .expiresAt(Instant.now().plus(CACHE_TTL))
                    .build();
            cacheMapper.insert(cache);
        } catch (JsonProcessingException e) {
            // 캐시 적재 실패는 조회 결과 반환을 막지 않는다(graceful degradation).
        }
    }

    private KpiQueryResult deserialize(String json, Map<String, Object> filters) {
        try {
            KpiQueryResult cached = objectMapper.readValue(json, KpiQueryResult.class);
            // 캐시본의 filters 는 직렬화 타입 손실 가능 — 현재 요청 filters 로 대체.
            return new KpiQueryResult(cached.items(), cached.totalCount(), cached.hasMore(), filters);
        } catch (JsonProcessingException e) {
            // 손상된 캐시는 빈 결과로 안전 처리(상위에서 미스 취급은 하지 않음).
            return new KpiQueryResult(List.of(), 0, false, filters);
        }
    }
}
