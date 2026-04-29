package kr.co.ircp.cms.domain.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 컬렉션 생성 요청 DTO.
 * REQ-MEDIA-005-D-1
 */
public record MediaCollectionCreateRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        boolean isPublic
) {
}
