package kr.co.ircp.cms.domain.media.dto;

import jakarta.validation.constraints.Size;
import kr.co.ircp.cms.domain.media.entity.LicenseType;

import java.util.List;

/**
 * 미디어 자산 메타데이터 수정 요청.
 * REQ-MEDIA-003-D (PUT /api/v1/media/{uuid})
 */
public record MediaUpdateRequest(
        @Size(max = 500) String altText,
        String description,
        List<String> tags,
        LicenseType licenseType,
        @Size(max = 200) String copyrightHolder,
        String usageRestriction
) {
}
