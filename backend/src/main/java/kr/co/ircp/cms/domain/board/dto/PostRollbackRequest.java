package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 게시글 롤백 요청 DTO.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M3 — 특정 버전으로의 롤백도 하나의 리비전이므로
 * 낙관적 잠금을 적용한다. {@code expectedVersion} 은 클라이언트가 알고 있는 게시물 현재 버전으로,
 * 서버 현재 버전과 다르면 409. 누락 시 400.
 */
public record PostRollbackRequest(
        @NotNull Integer expectedVersion
) {
}
