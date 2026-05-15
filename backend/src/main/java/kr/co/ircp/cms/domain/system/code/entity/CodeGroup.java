package kr.co.ircp.cms.domain.system.code.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 공통코드 그룹 엔티티.
 *
 * <p>REQ-SYSTEM-004-D — 공통코드 그룹 CRUD.
 * 사용 중인 코드가 있으면 그룹 삭제 불가 (RESTRICT).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeGroup {

    private Long id;
    private String groupCode;
    private String name;
    private String description;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
