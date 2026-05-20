package kr.co.ircp.cms.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 게시글 작성 요청 DTO.
 * REQ-BOARD-002-C: 게시글 생성
 */
public record PostCreateRequest(
        // @JsonAlias: 프론트엔드가 "bbsId"로 전송하는 경우도 허용
        @NotNull @JsonAlias("bbsId") Long bbsMasterId,
        @NotBlank @Size(max = 500) String title,
        @NotBlank String contentHtml,
        String contentText,
        boolean isNotice,
        Instant noticeFrom,
        Instant noticeUntil,
        boolean isSecret,
        String anonymousName,
        String anonymousPwd,
        String metadata
) {
}
