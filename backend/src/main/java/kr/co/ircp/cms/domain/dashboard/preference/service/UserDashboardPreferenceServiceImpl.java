package kr.co.ircp.cms.domain.dashboard.preference.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.dashboard.preference.dto.PreferenceResponse;
import kr.co.ircp.cms.domain.dashboard.preference.dto.PreferenceUpdateRequest;
import kr.co.ircp.cms.domain.dashboard.preference.entity.UserDashboardPreference;
import kr.co.ircp.cms.domain.dashboard.preference.exception.PreferenceConflictException;
import kr.co.ircp.cms.domain.dashboard.preference.repository.UserDashboardPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 — UserDashboardPreferenceService 구현.
 *
 * <p>hidden_widget_instance_ids 는 JSON 문자열로 DB 에 보관하고, 본 서비스에서
 * {@link ObjectMapper} 로 직렬화/역직렬화한다. 별도의 type handler 를 도입하지 않는다
 * (단순성 우선 + 기존 dashboard 매퍼 패턴과 일관).
 */
// @MX:ANCHOR: [AUTO] UserDashboardPreferenceServiceImpl — Controller + LayoutService(cleanup) 에서 참조
// @MX:REASON: fan_in >= 2 (Controller, LayoutDeleteCleanup) + SPEC 핵심 진입점 — 변경 시 회귀 큼
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 REQ-DP-001~002
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDashboardPreferenceServiceImpl implements UserDashboardPreferenceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, List<String>>> HIDDEN_TYPE =
            new TypeReference<>() {
            };

    private final UserDashboardPreferenceMapper mapper;

    @Override
    @Transactional
    public PreferenceResponse getOrCreate(Long userId) {
        UserDashboardPreference pref = mapper.findByUserId(userId).orElse(null);
        if (pref == null) {
            mapper.upsertDefaults(userId);
            pref = mapper.findByUserId(userId)
                    .orElseThrow(() -> new IllegalStateException(
                            "user_dashboard_preference upsert 실패. userId=" + userId));
        }
        return PreferenceResponse.from(pref);
    }

    @Override
    @Transactional
    public PreferenceResponse update(Long userId, PreferenceUpdateRequest req) {
        ensureExists(userId);
        int updated = mapper.patch(
                userId,
                req.theme(),
                req.density(),
                req.fontScale(),
                req.colorPalettePreference(),
                req.sidebarCollapsed(),
                req.expectedUpdatedAt()
        );
        if (updated == 0) {
            throw new PreferenceConflictException(
                    "다른 세션에서 환경설정이 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        }
        return getOrCreate(userId);
    }

    @Override
    @Transactional
    public PreferenceResponse reset(Long userId) {
        ensureExists(userId);
        mapper.resetStyleToDefault(userId);
        return getOrCreate(userId);
    }

    @Override
    @Transactional
    public PreferenceResponse toggleVisibility(Long userId, Long layoutId, String instanceId, boolean hidden) {
        UserDashboardPreference pref = ensureExists(userId);
        Map<String, List<String>> map = parseHidden(pref.getHiddenWidgetInstanceIds());
        String key = String.valueOf(layoutId);
        List<String> list = map.computeIfAbsent(key, k -> new ArrayList<>());

        if (hidden) {
            if (!list.contains(instanceId)) {
                list.add(instanceId);
            }
        } else {
            list.remove(instanceId);
        }

        mapper.updateHiddenWidgetInstanceIds(userId, serialize(map));
        return getOrCreate(userId);
    }

    @Override
    @Transactional
    public PreferenceResponse showAllWidgets(Long userId, Long layoutId) {
        UserDashboardPreference pref = ensureExists(userId);
        Map<String, List<String>> map = parseHidden(pref.getHiddenWidgetInstanceIds());
        map.put(String.valueOf(layoutId), new ArrayList<>());

        mapper.updateHiddenWidgetInstanceIds(userId, serialize(map));
        return getOrCreate(userId);
    }

    @Override
    @Transactional
    public void cleanupForLayout(Long userId, Long layoutId) {
        UserDashboardPreference pref = mapper.findByUserId(userId).orElse(null);
        if (pref == null) {
            return; // hidden 이 없는 사용자 — no-op
        }
        Map<String, List<String>> map = parseHidden(pref.getHiddenWidgetInstanceIds());
        if (map.remove(String.valueOf(layoutId)) == null) {
            return; // 해당 layout 키가 없으면 갱신 불필요
        }
        mapper.updateHiddenWidgetInstanceIds(userId, serialize(map));
    }

    // ── 내부 유틸 ──────────────────────────────────────────────────────────────

    private UserDashboardPreference ensureExists(Long userId) {
        UserDashboardPreference pref = mapper.findByUserId(userId).orElse(null);
        if (pref == null) {
            mapper.upsertDefaults(userId);
            pref = mapper.findByUserId(userId).orElseThrow();
        }
        return pref;
    }

    private static Map<String, List<String>> parseHidden(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, List<String>> parsed = MAPPER.readValue(json, HIDDEN_TYPE);
            return new LinkedHashMap<>(parsed);   // 변경 가능한 사본
        } catch (JsonProcessingException e) {
            // 깨진 JSON 은 빈 맵으로 폴백 — 사용자 데이터 손상은 SPEC 회피보다 안전.
            return new LinkedHashMap<>();
        }
    }

    private static String serialize(Map<String, List<String>> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("hidden_widget_instance_ids 직렬화 실패", e);
        }
    }
}
