package kr.co.ircp.cms.domain.approval.dto;

import java.time.Instant;

/**
 * 승인 대기 사용자 요약 DTO (대기열 목록·상세 응답).
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-007/009.
 *
 * @param userId         사용자 PK
 * @param username       로그인 아이디 (공개 가입자는 email 과 동일)
 * @param email          이메일
 * @param name           이름
 * @param createdAt      가입 신청 일시
 * @param organizationId 소속 조직 ID (공개 가입자는 null)
 */
public record UserApprovalSummary(
        Long userId,
        String username,
        String email,
        String name,
        Instant createdAt,
        Long organizationId
) {
}
