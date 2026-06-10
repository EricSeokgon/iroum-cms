package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 발간자료 카테고리 수정 요청 DTO.
 * REQ-PCA-002: 어드민 카테고리 수정
 */
public record PublicationCategoryUpdateRequest(

        @NotBlank @Size(max = 200)
        String name,

        int sortOrder,

        @NotBlank
        @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "status 는 ACTIVE 또는 INACTIVE 여야 합니다.")
        String status
) {
}
