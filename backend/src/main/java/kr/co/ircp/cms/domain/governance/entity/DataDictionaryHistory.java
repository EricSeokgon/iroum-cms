package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * data_dictionary 변경 이력.
 *
 * <p>SPEC-CMS-009 REQ-GOV-003: 컬럼별(field_changed) old_value→new_value 기록.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataDictionaryHistory {

    private Long id;
    private Long dictionaryId;
    private String fieldChanged;
    private String oldValue;
    private String newValue;
    private Long changedBy;
    private Instant changedAt;
}
