package kr.co.ircp.cms.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

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
        String metadata,
        // SPEC-CMS-AI-004: AI 스마트 태그 (선택, 공백 허용, 최대 5개). null/미전송 시 빈 목록 처리.
        List<String> tags
) {
}
