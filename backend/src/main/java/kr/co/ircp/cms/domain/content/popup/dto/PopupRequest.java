package kr.co.ircp.cms.domain.content.popup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * 팝업 생성/수정 요청 DTO.
 * REQ-CONTENT-008-D-1: 팝업 등록 (show_from < show_until, ROLE 타겟 시 역할 코드 필수)
 */
public record PopupRequest(
        @NotNull Long siteId,
        @NotBlank String title,
        @NotBlank String contentHtml,
        String position,
        Integer xOffset,
        Integer yOffset,
        Integer width,
        Integer height,
        @NotNull Instant showFrom,
        @NotNull Instant showUntil,
        Boolean showTodayClose,
        Integer displayPriority,
        String targetType,
        List<String> targetRoleCodes
) {}
