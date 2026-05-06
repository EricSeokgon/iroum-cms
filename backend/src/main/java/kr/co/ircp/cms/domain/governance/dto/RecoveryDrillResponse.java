package kr.co.ircp.cms.domain.governance.dto;

import kr.co.ircp.cms.domain.governance.entity.RecoveryDrillLog;

import java.time.Instant;
import java.time.LocalDate;

public record RecoveryDrillResponse(
        Long id,
        LocalDate drillDate,
        String drillType,
        String result,
        Integer rtoActualMin,
        Integer rpoActualMin,
        Integer rtoTargetMin,
        Integer rpoTargetMin,
        Long performedBy,
        String checklistJson,
        String notes,
        Instant createdAt
) {

    public static RecoveryDrillResponse from(RecoveryDrillLog r) {
        return new RecoveryDrillResponse(
                r.getId(), r.getDrillDate(), r.getDrillType(), r.getResult(),
                r.getRtoActualMin(), r.getRpoActualMin(),
                r.getRtoTargetMin(), r.getRpoTargetMin(),
                r.getPerformedBy(), r.getChecklistJson(), r.getNotes(),
                r.getCreatedAt());
    }
}
