package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 발간자료 카테고리 생성 요청 DTO.
 * REQ-PCA-001: 어드민 카테고리 생성
 */
public record PublicationCategoryCreateRequest(

        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "code는 대문자·숫자·언더스코어만 허용됩니다.")
        String code,

        @NotBlank @Size(max = 200)
        String name,

        /** null 이면 루트(depth 1) 카테고리로 생성. */
        Long parentId,

        int sortOrder
) {
    public PublicationCategoryCreateRequest {
        if (sortOrder < 0) sortOrder = 0;
    }
}
