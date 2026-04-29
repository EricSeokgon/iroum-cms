package kr.co.ircp.cms.domain.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.co.ircp.cms.domain.media.entity.LicenseType;

import java.util.List;

/**
 * 미디어 단건 업로드 요청 DTO.
 * alt_text는 IMAGE 타입인 경우 READY 전이 전에 필수 (DB CHECK 제약).
 * REQ-MEDIA-001-D, REQ-MEDIA-004-D-4
 */
public record MediaUploadRequest(
        @Size(max = 500) String altText,
        String description,
        @NotNull LicenseType licenseType,
        /** CC_BY·CC_BY_NC인 경우 필수 (서비스 레이어 검증) */
        @Size(max = 200) String copyrightHolder,
        List<String> tags,
        String usageRestriction
) {
}
