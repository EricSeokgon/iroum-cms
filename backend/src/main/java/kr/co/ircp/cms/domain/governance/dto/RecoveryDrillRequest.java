package kr.co.ircp.cms.domain.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecoveryDrillRequest(
        @NotNull LocalDate drillDate,
        @NotBlank String drillType,
        @NotBlank String result,
        Integer rtoActualMin,
        Integer rpoActualMin,
        Integer rtoTargetMin,
        Integer rpoTargetMin,
        Long performedBy,
        String checklistJson,
        String notes
) {}
