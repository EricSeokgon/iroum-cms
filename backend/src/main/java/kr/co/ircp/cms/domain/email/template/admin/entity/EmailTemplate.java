package kr.co.ircp.cms.domain.email.template.admin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 이메일 템플릿 엔티티 (email_template).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-001 — 관리자 CRUD 대상.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate {

    private Long id;
    private String code;
    private String name;
    private String templateType;
    private String language;
    private String subject;
    private String bodyHtml;
    private String bodyText;
    /** 필수 변수 정의 [{name, required, description}] — JSONB. */
    private List<Map<String, Object>> variables;
    private Boolean isActive;
    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
