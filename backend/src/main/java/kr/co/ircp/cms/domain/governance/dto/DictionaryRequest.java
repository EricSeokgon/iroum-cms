package kr.co.ircp.cms.domain.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * data_dictionary 생성·수정 요청 DTO.
 *
 * <p>SPEC-CMS-009 REQ-GOV-001~003.
 */
public record DictionaryRequest(
        @NotBlank @Size(max = 80) String tableName,
        @NotBlank @Size(max = 80) String columnName,
        @NotBlank @Size(max = 200) String logicalNameKo,
        @Size(max = 200) String logicalNameEn,
        @NotBlank String dataDomain,
        @NotBlank @Size(max = 50) String dataType,
        String description,
        Boolean isPii,
        Boolean isRequired,
        String status
) {}
