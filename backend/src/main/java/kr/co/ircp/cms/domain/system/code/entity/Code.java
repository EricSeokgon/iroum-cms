package kr.co.ircp.cms.domain.system.code.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 공통코드 엔티티.
 *
 * <p>REQ-SYSTEM-004-D — 공통코드 CRUD.
 * (group_code, code) UNIQUE 제약으로 중복 방지.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Code {

    private Long id;
    private String groupCode;
    private String code;
    private String name;
    private String description;
    private Integer sortOrder;
    private String status;
    /** 확장용 JSONB 자유 필드 */
    private String extraData;
    private Instant createdAt;
    private Instant updatedAt;
}
