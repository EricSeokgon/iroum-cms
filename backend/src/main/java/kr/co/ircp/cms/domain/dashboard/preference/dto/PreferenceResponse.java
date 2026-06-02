package kr.co.ircp.cms.domain.dashboard.preference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import kr.co.ircp.cms.domain.dashboard.preference.entity.UserDashboardPreference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7 GET /preference 응답.
 *
 * <p>{@code hidden_widget_instance_ids} 는 JSON 객체로 노출
 * ({@code Map<String, List<String>>}). 키는 layout_id 의 문자열 표현이다.
 */
public record PreferenceResponse(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("hidden_widget_instance_ids") Map<String, List<String>> hiddenWidgetInstanceIds,
        String theme,
        String density,
        @JsonProperty("font_scale") BigDecimal fontScale,
        @JsonProperty("color_palette_preference") String colorPalettePreference,
        @JsonProperty("sidebar_collapsed") boolean sidebarCollapsed,
        @JsonProperty("refresh_interval_seconds") Integer refreshIntervalSeconds,
        @JsonProperty("schema_version") short schemaVersion,
        @JsonProperty("updated_at") Instant updatedAt
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static PreferenceResponse from(UserDashboardPreference e) {
        Map<String, List<String>> hidden = parseHidden(e.getHiddenWidgetInstanceIds());
        return new PreferenceResponse(
                e.getUserId(),
                hidden,
                e.getTheme(),
                e.getDensity(),
                e.getFontScale(),
                e.getColorPalettePreference(),
                e.isSidebarCollapsed(),
                e.getRefreshIntervalSeconds(),
                e.getSchemaVersion(),
                e.getUpdatedAt()
        );
    }

    private static Map<String, List<String>> parseHidden(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            MapType type = MAPPER.getTypeFactory().constructMapType(
                    java.util.LinkedHashMap.class,
                    MAPPER.getTypeFactory().constructType(String.class),
                    MAPPER.getTypeFactory().constructCollectionType(java.util.ArrayList.class, String.class)
            );
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException ex) {
            // 깨진 JSON 은 빈 맵으로 폴백 — 사용자 환경설정 손실은 SPEC 회피보다 안전한 동작.
            return Collections.emptyMap();
        }
    }
}
