package kr.co.ircp.cms.domain.dashboard.preference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7 REQ-DP-003-2: PATCH /layouts/{id}/positions 요청.
 *
 * <p>본 요청은 위젯 인스턴스 N개의 새 position 좌표를 일괄 갱신한다. 일부 인스턴스가 누락되면
 * 해당 위젯의 position 은 변경되지 않는다 (부분 갱신).
 */
public record PositionPatchRequest(
        @NotEmpty(message = "위치 변경 대상이 최소 1개 필요합니다.")
        @Valid
        List<PositionEntry> entries,

        @JsonProperty("expected_updated_at")
        Instant expectedUpdatedAt
) {

    /** 단건 위젯 인스턴스의 새 좌표. */
    public record PositionEntry(
            @JsonProperty("instance_id")
            @NotBlank(message = "instance_id 는 필수입니다.")
            String instanceId,

            @NotNull(message = "position 은 필수입니다.")
            @Valid
            Position position
    ) {
    }

    /** 12-grid 좌표/크기. */
    public record Position(
            @Min(value = 0, message = "x 는 0 이상이어야 합니다.") int x,
            @Min(value = 0, message = "y 는 0 이상이어야 합니다.") int y,
            @Min(value = 1, message = "w 는 1 이상이어야 합니다.") int w,
            @Min(value = 1, message = "h 는 1 이상이어야 합니다.") int h
    ) {
    }
}
