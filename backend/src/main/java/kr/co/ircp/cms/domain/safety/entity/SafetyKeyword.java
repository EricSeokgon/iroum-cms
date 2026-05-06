package kr.co.ircp.cms.domain.safety.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 안전 키워드 사전 엔티티.
 * REQ-SAFETY-002-D: 키워드 가중치 매칭의 사전
 */
@Data
@Builder
public class SafetyKeyword {
    private Long id;
    private String category;   // INDUSTRY / PROCESS / HAZARD / EQUIPMENT
    private String code;
    private String term;
    private String description;
    private String status;     // ACTIVE / INACTIVE
    private Instant createdAt;
}
