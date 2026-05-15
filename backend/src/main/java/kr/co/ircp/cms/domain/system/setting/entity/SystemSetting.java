package kr.co.ircp.cms.domain.system.setting.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 시스템 설정 엔티티.
 *
 * <p>REQ-SYSTEM-005-D — key-value 기반 시스템 설정.
 * value_type: STRING | INT | BOOL | JSON
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSetting {

    private String key;
    private String value;
    /** STRING | INT | BOOL | JSON */
    private String valueType;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
