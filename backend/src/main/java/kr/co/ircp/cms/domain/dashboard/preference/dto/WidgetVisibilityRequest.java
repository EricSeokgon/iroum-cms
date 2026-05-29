package kr.co.ircp.cms.domain.dashboard.preference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7 PATCH /preference/widgets/{layoutId}/hidden 요청.
 *
 * <p>REQ-DP-001-1 (hidden=true): instance_id 를 hidden 배열에 추가
 * <p>REQ-DP-001-2 (hidden=false): instance_id 를 hidden 배열에서 제거
 */
public record WidgetVisibilityRequest(
        @JsonProperty("instance_id")
        @NotBlank(message = "instance_id 는 필수입니다.")
        String instanceId,

        @NotNull(message = "hidden 은 필수입니다.")
        Boolean hidden
) {
}
