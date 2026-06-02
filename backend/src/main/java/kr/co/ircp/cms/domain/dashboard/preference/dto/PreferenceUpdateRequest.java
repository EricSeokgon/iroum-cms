package kr.co.ircp.cms.domain.dashboard.preference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7 PATCH /preference 요청.
 *
 * <p>모든 필드 nullable — 변경하려는 필드만 전송. 검증 실패 시 400 BadRequest.
 *
 * <p>{@code expectedUpdatedAt} 가 전달되면 낙관적 잠금이 활성화된다.
 */
public record PreferenceUpdateRequest(
        @Pattern(regexp = "LIGHT|DARK|SYSTEM",
                message = "theme 은 LIGHT, DARK, SYSTEM 중 하나여야 합니다.")
        String theme,

        @Pattern(regexp = "COMPACT|NORMAL|COMFORTABLE",
                message = "density 는 COMPACT, NORMAL, COMFORTABLE 중 하나여야 합니다.")
        String density,

        @JsonProperty("font_scale")
        @DecimalMin(value = "0.875", message = "font_scale 최소값은 0.875 입니다.")
        @DecimalMax(value = "1.125", message = "font_scale 최대값은 1.125 입니다.")
        BigDecimal fontScale,

        @JsonProperty("color_palette_preference")
        @Pattern(regexp = "DEFAULT|COLORBLIND|MONOCHROME",
                message = "color_palette_preference 는 DEFAULT, COLORBLIND, MONOCHROME 중 하나여야 합니다.")
        String colorPalettePreference,

        @JsonProperty("sidebar_collapsed")
        Boolean sidebarCollapsed,

        // SPEC-CMS-DASHBOARD-REFRESH-001: 새로고침 주기(초). null = OFF, 비전송 = 변경 없음.
        // 허용값 검증(30/60/300/900/1800 또는 null)은 서비스 화이트리스트에서 수행한다.
        @JsonProperty("refresh_interval_seconds")
        Integer refreshIntervalSeconds,

        // true 일 때만 refresh_interval_seconds 를 실제 적용 (null = OFF 표현을 허용하기 위한 presence flag).
        @JsonProperty("has_refresh_interval_seconds")
        Boolean hasRefreshIntervalSeconds,

        @JsonProperty("expected_updated_at")
        Instant expectedUpdatedAt
) {
}
